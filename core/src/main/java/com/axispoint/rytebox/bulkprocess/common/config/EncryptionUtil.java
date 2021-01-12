package com.axispoint.rytebox.bulkprocess.common.config;

import java.security.GeneralSecurityException;
import java.security.spec.KeySpec;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import com.google.common.base.Charsets;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class EncryptionUtil {

    private final String secretKey;
    private final String salt;
    private final  String ivString;


    public String decrypt(String strToDecrypt) throws GeneralSecurityException {
        //try
        {
            byte[] iv = ivString.getBytes(Charsets.UTF_8);
            IvParameterSpec ivspec = new IvParameterSpec(iv);

            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            KeySpec spec = new PBEKeySpec(secretKey.toCharArray(), salt.getBytes(), 65536, 256);
            SecretKey tmp = factory.generateSecret(spec);
            SecretKeySpec secretKey = new SecretKeySpec(tmp.getEncoded(), "AES");

            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING");
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivspec);
            return new String(cipher.doFinal(Base64.getDecoder().decode(strToDecrypt)));        }
    }
}
