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
import com.axispoint.rytebox.bulkprocess.common.dto.DbCredentials;

@Slf4j
public class DbClientHolder {

    private final EncryptionUtil encryptionUtil;
    private final ObjectMapper mapper;
    private final String dbHost;
    private final String dbPort;

    MySQLPool _client = null;

    public DbClientHolder(EncryptionUtil encryptionUtil, ObjectMapper mapper, String dbHost, String dbPort) {
        this.encryptionUtil = encryptionUtil;
        this.mapper = mapper;
        this.dbHost = dbHost;
        this.dbPort = dbPort;
    }

    @SneakyThrows
    public void init(JsonNode credentialsJson) {
        DbCredentials credentials = mapper.convertValue(credentialsJson, DbCredentials.class);
        _client = openPool(credentials);
    }

    @SneakyThrows
    public MySQLPool client() {
        return _client;
    }

    private MySQLPool openPool(DbCredentials credentials) throws GeneralSecurityException {
        MySQLConnectOptions connectOptions = new MySQLConnectOptions()
                  .setHost(dbHost)
                  .setPort(Integer.valueOf(dbPort))
                  .setDatabase(credentials.getDatabase())
                  .setUser(credentials.getUsername())
                  .setPassword(encryptionUtil.decrypt(credentials.getEncryptedPassword()));

        PoolOptions poolOptions = new PoolOptions().setMaxSize(5);
        return  MySQLPool.pool(connectOptions, poolOptions);
    }
}
