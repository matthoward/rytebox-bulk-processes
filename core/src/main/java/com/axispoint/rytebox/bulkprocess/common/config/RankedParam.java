package com.axispoint.rytebox.bulkprocess.common.config;

import com.amazonaws.services.simplesystemsmanagement.model.Parameter;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Value
@Slf4j
public class RankedParam {
    private String name;
    private String value;

    static RankedParam of(Parameter ssmParam) {
        log.debug("creating param: {} = {}", ssmParam.getName(), ssmParam.getValue());
        return new RankedParam(ssmParam.getName(), ssmParam.getValue());
    }

    public String getParamName() {
        return StringUtils.substringAfterLast(name, "/");
    }

    public Integer getPriority() {
        int priority = 0;
        String dir = StringUtils.substringAfterLast(StringUtils.substringBeforeLast(name, "/"), "/");
        if ("application".equals(dir)) {
            priority = 3;
        } else if (dir.startsWith("application_")) {
            priority = 2;
        } else if (dir.contains("_")) {
            priority = 1;
        }
        log.trace("param {}/{} assigned priority={}", dir, getParamName(), priority);
        return priority;
    }
}
