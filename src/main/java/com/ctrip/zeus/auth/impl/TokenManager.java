package com.ctrip.zeus.auth.impl;

import com.ctrip.zeus.dal.core.AuthPrivateKeyDao;
import com.ctrip.zeus.dal.core.AuthPrivateKeyDo;
import com.ctrip.zeus.dal.core.AuthPrivateKeyEntity;
import com.ctrip.zeus.support.DaoFactory;
import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.DESKeySpec;
import java.security.Key;

/**
 * User: mag
 * Date: 5/14/2015
 * Time: 5:27 PM
 */
public class TokenManager {
    private static final Logger logger = LoggerFactory.getLogger(TokenManager.class);
    public static final String ALGORITHM = "DES";
    private static String privateKey = "slbPrivateKey";

    static {
        try {
            AuthPrivateKeyDao dao = new DaoFactory().getDao(AuthPrivateKeyDao.class);
            AuthPrivateKeyDo privateKeyDo = dao.findFirst(AuthPrivateKeyEntity.READSET_FULL);
            privateKey = privateKeyDo.getPrivateKey();
        }catch (Exception e){
            logger.error("error fetch private key from db.",e);
        }
    }

    public static String generateToken() {
        long currTime = System.currentTimeMillis();
        byte[] timeBytes = toBytes(currTime);
        try {
            Key k = toKey(privateKey.getBytes("UTF-8"));
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, k);
            return encryptBASE64(cipher.doFinal(timeBytes));
        }catch (Exception e){
            logger.error("error when generate token",e);
            throw new RuntimeException(e);
        }
    }

    public static boolean validateToken(String token) {
        try {
            Key key = toKey(privateKey.getBytes("UTF-8"));
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key);
            long currTime = System.currentTimeMillis();
            long decryptedTime = toLong(cipher.doFinal(decryptBASE64(token)));
            if ((currTime - decryptedTime) < 60000){
                return true;
            }
        } catch (Exception e) {
            logger.error("validate token fail!",e);
        }
        return false;
    }

    private static byte[] decryptBASE64(String key) throws Exception {
        return new Base64(-1).decode(key);
    }


    private static String encryptBASE64(byte[] key) throws Exception {
        return new Base64(-1).encodeToString(key);
    }

    private static Key toKey(byte[] key) throws Exception {
        DESKeySpec dks = new DESKeySpec(key);
        SecretKeyFactory keyFactory = SecretKeyFactory.getInstance(ALGORITHM);
        SecretKey secretKey = keyFactory.generateSecret(dks);
        return secretKey;
    }

    private static byte[] toBytes(long value){
        byte[] result = new byte[8];
        long tmp = value;
        for (int i = 7; i >= 0; i--) {
            result[i] = (byte) (tmp & (0xFFL));
            tmp = tmp >> 8;
        }
        return result;
    }

    private static long toLong(byte[] value) {
        long rt = 0;
        for (int i = 0; i < 8; i++) {
            int add = value[i] & (0xFF);
            rt = rt << 8;
            rt += add;
        }
        return rt;
    }

    public static void main(String[] args) throws Exception{
        String token = TokenManager.generateToken();
        System.out.println("token:" + token);
        System.out.println("validate token:" + TokenManager.validateToken(token));
    }
}
