package com.axispoint.rytebox.bulkprocess.common.config;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toCollection;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagement;
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagementClientBuilder;
import com.amazonaws.services.simplesystemsmanagement.model.GetParametersByPathRequest;
import com.amazonaws.services.simplesystemsmanagement.model.GetParametersByPathResult;
import com.amazonaws.services.simplesystemsmanagement.model.Parameter;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.spi.ConfigSource;

/*
    See https://cloud.spring.io/spring-cloud-static/spring-cloud-aws/2.0.0.RELEASE/multi/multi__cloud_environment.html#_integrating_your_spring_cloud_application_with_the_aws_parameter_store
    Our core services use the AWS param store for holding some runtime info like secrets and env-specific configs.
    We can't use the spring cloud lib in quarkus, but this class mimics the behavior of prioritizing application_uat/ configs over application/ configs
    and dqi-export_uat/ configs over application_uat/ configs

    TODO: look into config-dependent vars... e.g. /config/application/auth0.audience = https://${ENVIRONMENT_NAME}.rytebox.net requires evaluation of ENVIRONMENT_NAME
    TODO: (related to above) look into smallrye ConfigSourceFactory to inject config arguments into a config source
          https://github.com/smallrye/smallrye-config/blob/47ffb8e626d9c5122cab4c4fe46c0b85b28d2052/doc/modules/ROOT/pages/config-sources/config-sources.adoc#config-source-factory
          e.g. to configure the environment name in this class instead of doing a System.getenv() call
 */

@Slf4j
public class AwsParamStoreConfig implements ConfigSource {
    private final String AWS_REGION =  Optional.ofNullable(System.getenv("AWS_REGION")).orElse("us-east-1");
    private final List<String> ssmPaths= List.of("/config/application", "/config/application_" +
            Optional.ofNullable(System.getenv("ENVIRONMENT_NAME")).orElse("dev"));

    public final Map<String, String> ssm = fetchSsmProperties();

    private AWSSimpleSystemsManagement ssmClient;

    @Override
    public Map<String, String> getProperties() {
        return ssm;
    }

    @Override
    public String getValue(String key) {
        return ssm.get(key);
    }

    @Override
    public String getName() {
        return "aws-paramstore-source";
    }

    private Map<String, String> fetchSsmProperties() {
        System.out.println("getting ssmPaths = " +  ssmPaths);
        if (ssm != null) {
            return ssm;
        }

        if (ssmClient == null) {
            ssmClient = AWSSimpleSystemsManagementClientBuilder
                    .standard()
                    .withCredentials(new DefaultAWSCredentialsProviderChain())
                    .withRegion(AWS_REGION)
                    .build();
        }

        log.info("fetching AWS parameter store properties...");

        return filterByPriority(
                ssmPaths.stream()
                    .map(this::buildConfigRequest)
                    .flatMap(this::getParameters)
                    .map(RankedParam::of)
                )
                .collect(Collectors.toMap(RankedParam::getParamName, RankedParam::getValue));
    }

    private GetParametersByPathRequest buildConfigRequest(String path) {
        log.info("fetching SSM parameters in: {}", path);
        return new GetParametersByPathRequest()
                    .withPath(path)
                    .withRecursive(true)
                    .withWithDecryption(true);
    }

    private Stream<Parameter> getParameters(GetParametersByPathRequest request) {
        GetParametersByPathResult result = ssmClient.getParametersByPath(request);
        Stream<Parameter> params = result.getParameters().stream();

        if (result.getNextToken() != null) {
            Stream.concat(params, getParameters(request.withNextToken(result.getNextToken())));
        }

        return params;
    }

    private Stream<RankedParam> filterByPriority(Stream<RankedParam> params) {
        return params
                .sorted(comparing(RankedParam::getPriority))
                .collect(collectingAndThen(toCollection(() -> new TreeSet<>(comparing(RankedParam::getParamName))), Collection::stream));
    }

}