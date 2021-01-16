package com.axispoint.rytebox.bulkprocess.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DbCredentials {
    private String database;
    private String username;
    private String encryptedPassword;
}
