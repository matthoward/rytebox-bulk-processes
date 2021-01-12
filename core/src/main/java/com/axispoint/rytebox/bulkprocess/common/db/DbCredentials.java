package com.axispoint.rytebox.bulkprocess.common.db;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DbCredentials {
    private String host;
    private Integer port;
    private String database;
    private String username;
    private String encryptedPassword;
}
