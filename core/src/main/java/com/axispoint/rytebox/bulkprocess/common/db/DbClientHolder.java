package com.axispoint.rytebox.bulkprocess.common.db;

import java.security.GeneralSecurityException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.mutiny.mysqlclient.MySQLPool;
import io.vertx.mysqlclient.MySQLConnectOptions;
import io.vertx.sqlclient.PoolOptions;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import com.axispoint.rytebox.bulkprocess.common.config.EncryptionUtil;

@Slf4j
public class DbClientHolder {

    private final EncryptionUtil encryptionUtil;
    private final ObjectMapper mapper;

    MySQLPool _client = null;

    public DbClientHolder(EncryptionUtil encryptionUtil, ObjectMapper mapper) {
        log.info("instantiating DbClientHolder... ");
        this.encryptionUtil = encryptionUtil;
        this.mapper = mapper;
    }

    @SneakyThrows
    public void init(JsonNode credentialsJson) {
        DbCredentials credentials = mapper.convertValue(credentialsJson, DbCredentials.class);
        _client = openPool(credentials);
        log.info("DbClientHolder {}", this);
        log.info("initialized dbclient as {}", _client);
    }

    @SneakyThrows
    public MySQLPool client() {
        return _client;
    }

    private MySQLPool openPool(DbCredentials credentials) throws GeneralSecurityException {
        log.info("openPool with {}", credentials);
        MySQLConnectOptions connectOptions = new MySQLConnectOptions()
                  .setPort(credentials.getPort())
                  .setHost(credentials.getHost())
                  .setDatabase(credentials.getDatabase())
                  .setUser(credentials.getUsername())
                  .setPassword(encryptionUtil.decrypt(credentials.getEncryptedPassword()));

        PoolOptions poolOptions = new PoolOptions().setMaxSize(5);
        return  MySQLPool.pool(connectOptions, poolOptions);
    }
}
