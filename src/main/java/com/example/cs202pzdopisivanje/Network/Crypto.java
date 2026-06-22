package com.example.cs202pzdopisivanje.Network;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

public class Crypto {

    private static final byte[] KEY_BYTES = "WhatTheSigma1234".getBytes();
    private static final SecretKeySpec KEY = new SecretKeySpec(KEY_BYTES, "AES");
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int T_LENGTH_BIT = 128;
    private static final int IV_LENGTH_BYTE = 12;

    private static GCMParameterSpec generateIv() {
        byte[] iv = new byte[IV_LENGTH_BYTE];
        new SecureRandom().nextBytes(iv);
        return new GCMParameterSpec(T_LENGTH_BIT, iv);
    }

    public static byte[] encrypt(Object obj) throws NoSuchPaddingException, NoSuchAlgorithmException,
            InvalidAlgorithmParameterException, InvalidKeyException,
            BadPaddingException, IllegalBlockSizeException, IOException {

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bos);
        oos.writeObject(obj);
        oos.flush();
        byte[] serialized = bos.toByteArray();

        GCMParameterSpec iv = generateIv();
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, KEY, iv);
        byte[] encrypted = cipher.doFinal(serialized);

        byte[] result = new byte[IV_LENGTH_BYTE + encrypted.length];
        System.arraycopy(iv.getIV(), 0, result, 0, IV_LENGTH_BYTE);
        System.arraycopy(encrypted, 0, result, IV_LENGTH_BYTE, encrypted.length);

        return result;
    }

    public static Object decrypt(byte[] data) throws NoSuchPaddingException, NoSuchAlgorithmException,
            InvalidAlgorithmParameterException, InvalidKeyException,
            BadPaddingException, IllegalBlockSizeException, IOException, ClassNotFoundException {

        byte[] iv = new byte[IV_LENGTH_BYTE];
        byte[] encrypted = new byte[data.length - IV_LENGTH_BYTE];
        System.arraycopy(data, 0, iv, 0, IV_LENGTH_BYTE);
        System.arraycopy(data, IV_LENGTH_BYTE, encrypted, 0, encrypted.length);

        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, KEY, new GCMParameterSpec(T_LENGTH_BIT, iv));
        byte[] serialized = cipher.doFinal(encrypted);

        ByteArrayInputStream bis = new ByteArrayInputStream(serialized);
        ObjectInputStream ois = new ObjectInputStream(bis);
        return ois.readObject();
    }
}
