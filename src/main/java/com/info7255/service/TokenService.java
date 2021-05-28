package com.info7255.service;

import java.io.UnsupportedEncodingException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.json.JSONObject;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestHeader;

@Service
public class TokenService {
	
	private static String finalKey = "0123456789abcdef";
	
	public String generateToken() throws UnsupportedEncodingException, NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException, InvalidKeyException, BadPaddingException, IllegalBlockSizeException {
		String initVector = "RandomInitVector";

        JSONObject object = new JSONObject();
        object.put("organization", "INFO7255");
        object.put("user", "Vineet");

        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MINUTE, 30);
        Date date = calendar.getTime();
        SimpleDateFormat df = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
        object.put("ttl", df.format(date));

        String token = object.toString();
        IvParameterSpec iv = new IvParameterSpec(initVector.getBytes("UTF-8"));
        SecretKeySpec skeySpec = new SecretKeySpec(finalKey.getBytes("UTF-8"), "AES");
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING");
        cipher.init(Cipher.ENCRYPT_MODE, skeySpec, iv);

        byte[] encrypted = cipher.doFinal(token.getBytes());

        String finalToken = org.apache.tomcat.util.codec.binary.Base64.encodeBase64String(encrypted);
        return finalToken;
	}

    public String authorizeToken(@RequestHeader HttpHeaders headers) {
    	String token = headers.getFirst("Authorization");
        if (token == null || token.isEmpty()) {
            return "No token Found";
        }

        if (!token.contains("Bearer ")) {
            return "Improper Format of Token";
        }

        token = token.substring(7);
        boolean authorized = authorize(token);

        if (authorized == false) {
            return "Token is Expired or Invalid Token";
        }
        return "Valid Token";
    }

    public boolean authorize(String token) {
    	try {
            String initVector = "RandomInitVector";
            IvParameterSpec iv = new IvParameterSpec(initVector.getBytes("UTF-8"));
            SecretKeySpec skeySpec = new SecretKeySpec(finalKey.getBytes("UTF-8"), "AES");

            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING");
            cipher.init(Cipher.DECRYPT_MODE, skeySpec, iv);
            byte[] original = cipher.doFinal(org.apache.tomcat.util.codec.binary.Base64.decodeBase64(token));
            String entityDecoded = new String(original);

            JSONObject jsonobj = new JSONObject(entityDecoded);
            Object arrayOfTests = jsonobj.get("ttl");
            Calendar calendar = Calendar.getInstance();
            Date date = calendar.getTime();
            String getDate = arrayOfTests.toString();
            SimpleDateFormat formatter = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");

            Date end = formatter.parse(getDate);
            Date start = formatter.parse(formatter.format(date));

            if (!start.before(end)) {
                return false;
            }
        } catch (Exception e) {
            return false;
        }
        return true;
    };
}
