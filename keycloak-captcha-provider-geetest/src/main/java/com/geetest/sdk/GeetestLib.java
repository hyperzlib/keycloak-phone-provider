/*
  @(#)Result.JAVA, 2020年05月22日.
 * <p>
 * Copyright 2020 GEETEST, Inc. All rights reserved.
 * GEETEST PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */
package com.geetest.sdk;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jackson.JsonLoader;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
class GeetestValidateRequest {
    @JsonProperty("success")
    public int success;

    @JsonProperty("gt")
    public String gt;

    @JsonProperty("challenge")
    public String challenge;

    @JsonProperty("new_captcha")
    public boolean newCaptcha;
}

/**
 * sdk lib包，核心逻辑。
 *
 * @author liuquan@geetest.com
 */
public class GeetestLib {

    /**
     * 公钥
     */
    private String geetest_id;

    /**
     * 私钥
     */
    private String geetest_key;

    /**
     * 返回数据的封装对象
     */
    private GeetestLibResult libResult;

    /**
     * 调试开关，是否输出调试日志
     */
    private static final boolean IS_DEBUG = false;

    private static final String API_URL = "http://api.geetest.com";

    private static final String REGISTER_URL = "/register.php";

    private static final String VALIDATE_URL = "/validate.php";

    private static final String JSON_FORMAT = "1";

    private static final boolean NEW_CAPTCHA = true;

    private static final int HTTP_TIMEOUT_DEFAULT = 5000; // 单位：毫秒

    public static final String VERSION = "jave-servlet:3.1.0";

    /**
     * 极验二次验证表单传参字段 chllenge
     */
    public static final String GEETEST_CHALLENGE = "geetest_challenge";

    /**
     * 极验二次验证表单传参字段 validate
     */
    public static final String GEETEST_VALIDATE = "geetest_validate";

    /**
     * 极验二次验证表单传参字段 seccode
     */
    public static final String GEETEST_SECCODE = "geetest_seccode";

    /**
     * 极验验证API服务状态Session Key
     */
    public static final String GEETEST_SERVER_STATUS_SESSION_KEY = "gt_server_status";

    public GeetestLib(String geetest_id, String geetest_key) {
        this.geetest_id = geetest_id;
        this.geetest_key = geetest_key;
        this.libResult = new GeetestLibResult();
    }

    public void gtlog(String message) {
        if (IS_DEBUG) {
            System.out.println("gtlog: " + message);
        }
    }

    /**
     * 验证初始化
     */
    public GeetestLibResult register(String digestmod, Map<String, String> paramMap) {
        this.gtlog(String.format("register(): 开始验证初始化, digestmod=%s.", digestmod));
        String origin_challenge = this.requestRegister(paramMap);
        try {
            this.buildRegisterResult(origin_challenge, digestmod);
            this.gtlog(String.format("register(): 验证初始化, lib包返回信息=%s.", this.libResult));
            return this.libResult;
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 向极验发送验证初始化的请求，GET方式
     */
    private String requestRegister(Map<String, String> paramMap) {
        paramMap.put("gt", this.geetest_id);
        paramMap.put("json_format", JSON_FORMAT);
        paramMap.put("com/geetest/sdk", VERSION);
        String register_url = API_URL + REGISTER_URL;
        this.gtlog(String.format("requestRegister(): 验证初始化, 向极验发送请求, url=%s, params=%s.", register_url, paramMap));
        String origin_challenge;
        try {
            String resBody = this.httpGet(register_url, paramMap);
            this.gtlog(String.format("requestRegister(): 验证初始化, 与极验网络交互正常, 返回body=%s.", resBody));
            JsonNode jsonObject = JsonLoader.fromString(resBody);
            origin_challenge = jsonObject.get("challenge").asText();
        } catch (Exception e) {
            this.gtlog("requestRegister(): 验证初始化, 请求异常，后续流程走宕机模式, " + e.toString());
            origin_challenge = "";
        }
        return origin_challenge;
    }

    /**
     * 构建验证初始化接口返回数据
     */
    private void buildRegisterResult(String origin_challenge, String digestmod) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        // origin_challenge为空或者值为0代表失败
        if (origin_challenge == null || origin_challenge.isEmpty() || "0".equals(origin_challenge)) {
            // 本地随机生成32位字符串
            String challenge = UUID.randomUUID().toString().replaceAll("-", "");
            GeetestValidateRequest requestData = new GeetestValidateRequest(0, this.geetest_id,
                    challenge, NEW_CAPTCHA);
            this.libResult.setAll(0, mapper.writeValueAsString(requestData),
                    "请求极验register接口失败，后续流程走宕机模式");
        } else {
            String challenge;
            if ("md5".equals(digestmod)) {
                challenge = this.md5_encode(origin_challenge + this.geetest_key);
            } else if ("sha256".equals(digestmod)) {
                challenge = this.sha256_encode(origin_challenge + this.geetest_key);
            } else if ("hmac-sha256".equals(digestmod)) {
                challenge = this.hmac_sha256_encode(origin_challenge, this.geetest_key);
            } else {
                challenge = this.md5_encode(origin_challenge + this.geetest_key);
            }
            GeetestValidateRequest requestData = new GeetestValidateRequest(1, this.geetest_id,
                    challenge, NEW_CAPTCHA);
            this.libResult.setAll(1, mapper.writeValueAsString(requestData), "");
        }
    }

    /**
     * 正常流程下（即验证初始化成功），二次验证
     */
    public GeetestLibResult successValidate(String challenge, String validate, String seccode, Map<String, String> paramMap) {
        this.gtlog(String.format("successValidate(): 开始二次验证 正常模式, challenge=%s, validate=%s, seccode=%s.", challenge, validate, seccode));
        if (!this.checkParam(challenge, validate, seccode)) {
            this.libResult.setAll(0, "", "正常模式，本地校验，参数challenge、validate、seccode不可为空");
        } else {
            String response_seccode = this.requestValidate(challenge, validate, seccode, paramMap);
            if (response_seccode == null || response_seccode.isEmpty()) {
                this.libResult.setAll(0, "", "请求极验validate接口失败");
            } else if ("false".equals(response_seccode)) {
                this.libResult.setAll(0, "", "极验二次验证不通过");
            } else {
                this.libResult.setAll(1, "", "");
            }
        }
        this.gtlog(String.format("successValidate(): 二次验证 正常模式, lib包返回信息=%s.", this.libResult));
        return this.libResult;
    }

    /**
     * 异常流程下（即验证初始化失败，宕机模式），二次验证
     * 注意：由于是宕机模式，初衷是保证验证业务不会中断正常业务，所以此处只作简单的参数校验，可自行设计逻辑。
     */
    public GeetestLibResult failValidate(String challenge, String validate, String seccode) {
        this.gtlog(String.format("failValidate(): 开始二次验证 宕机模式, challenge=%s, validate=%s, seccode=%s.", challenge, validate, seccode));
        if (!this.checkParam(challenge, validate, seccode)) {
            this.libResult.setAll(0, "", "宕机模式，本地校验，参数challenge、validate、seccode不可为空.");
        } else {
            this.libResult.setAll(1, "", "");
        }
        this.gtlog(String.format("failValidate(): 二次验证 宕机模式, lib包返回信息=%s.", this.libResult));
        return this.libResult;
    }

    /**
     * 向极验发送二次验证的请求，POST方式
     */
    private String requestValidate(String challenge, String validate, String seccode, Map<String, String> paramMap) {
        paramMap.put("seccode", seccode);
        paramMap.put("json_format", JSON_FORMAT);
        paramMap.put("challenge", challenge);
        paramMap.put("com/geetest/sdk", VERSION);
        paramMap.put("captchaid", this.geetest_id);
        String validate_url = API_URL + VALIDATE_URL;
        this.gtlog(String.format("requestValidate(): 二次验证 正常模式, 向极验发送请求, url=%s, params=%s.", validate_url, paramMap));
        String response_seccode;
        try {
            String resBody = this.httpPost(validate_url, paramMap);
            this.gtlog(String.format("requestValidate(): 二次验证 正常模式, 与极验网络交互正常, 返回body=%s.", resBody));

            JsonNode jsonObject = JsonLoader.fromString(resBody);
            response_seccode = jsonObject.get("seccode").asText();
        } catch (Exception e) {
            this.gtlog("requestValidate(): 二次验证 正常模式, 请求异常, " + e.toString());
            response_seccode = "";
        }
        return response_seccode;
    }

    /**
     * 校验二次验证的三个参数，校验通过返回true，校验失败返回false
     */
    private boolean checkParam(String challenge, String validate, String seccode) {
        return !(challenge == null || challenge.trim().isEmpty() || validate == null || validate.trim().isEmpty() || seccode == null || seccode.trim().isEmpty());
    }

    /**
     * 发送GET请求，获取服务器返回结果
     */
    private String httpGet(String url, Map<String, String> paramMap) throws Exception {
        HttpURLConnection connection = null;
        InputStream inStream = null;
        try {
            Iterator<String> it = paramMap.keySet().iterator();
            StringBuilder paramStr = new StringBuilder();
            while (it.hasNext()) {
                String key = it.next();
                if (key == null || key.isEmpty() || paramMap.get(key) == null || paramMap.get(key).isEmpty()) {
                    continue;
                }
                paramStr.append("&").append(URLEncoder.encode(key, StandardCharsets.UTF_8))
                        .append("=").append(URLEncoder.encode(paramMap.get(key), StandardCharsets.UTF_8));
            }
            if (paramStr.length() != 0) {
                paramStr.replace(0, 1, "?");
            }
            url += paramStr.toString();
            URL getUrl = new URL(url);
            connection = (HttpURLConnection) getUrl.openConnection();
            connection.setConnectTimeout(HTTP_TIMEOUT_DEFAULT); // 设置连接主机超时（单位：毫秒）
            connection.setReadTimeout(HTTP_TIMEOUT_DEFAULT); // 设置从主机读取数据超时（单位：毫秒）
            connection.connect();
            if (connection.getResponseCode() == 200) {
                StringBuilder sb = new StringBuilder();
                byte[] buf = new byte[1024];
                inStream = connection.getInputStream();
                for (int n; (n = inStream.read(buf)) != -1; ) {
                    sb.append(new String(buf, 0, n, StandardCharsets.UTF_8));
                }
                return sb.toString();
            }
            return "";
        } catch (Exception e) {
            throw e;
        } finally {
            if (inStream != null) {
                inStream.close();
            }
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * 发送POST请求，获取服务器返回结果
     */
    private String httpPost(String url, Map<String, String> paramMap) throws Exception {
        HttpURLConnection connection = null;
        InputStream inStream = null;
        try {
            Iterator<String> it = paramMap.keySet().iterator();
            StringBuilder paramStr = new StringBuilder();
            while (it.hasNext()) {
                String key = it.next();
                if (key == null || key.isEmpty() || paramMap.get(key) == null || paramMap.get(key).isEmpty()) {
                    continue;
                }
                paramStr.append("&").append(URLEncoder.encode(key, StandardCharsets.UTF_8))
                        .append("=").append(URLEncoder.encode(paramMap.get(key), StandardCharsets.UTF_8));
            }
            if (paramStr.length() != 0) {
                paramStr.replace(0, 1, "");
            }
            URL postUrl = new URL(url);
            connection = (HttpURLConnection) postUrl.openConnection();
            connection.setConnectTimeout(HTTP_TIMEOUT_DEFAULT);// 设置连接主机超时（单位：毫秒）
            connection.setReadTimeout(HTTP_TIMEOUT_DEFAULT);// 设置从主机读取数据超时（单位：毫秒）
            connection.setRequestMethod("POST");
            connection.setDoInput(true);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            connection.connect();
            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(connection.getOutputStream(),
                    StandardCharsets.UTF_8);
            outputStreamWriter.write(paramStr.toString());
            outputStreamWriter.flush();
            outputStreamWriter.close();
            if (connection.getResponseCode() == 200) {
                StringBuilder sb = new StringBuilder();
                byte[] buf = new byte[1024];
                inStream = connection.getInputStream();
                for (int n; (n = inStream.read(buf)) != -1; ) {
                    sb.append(new String(buf, 0, n, StandardCharsets.UTF_8));
                }
                inStream.close();
                connection.disconnect();
                return sb.toString();
            }
            return "";
        } catch (Exception e) {
            throw e;
        } finally {
            if (inStream != null) {
                inStream.close();
            }
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * md5 加密
     */
    private String md5_encode(String value) {
        String re_md5 = "";
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(value.getBytes());
            byte[] b = md.digest();
            int i;
            StringBuilder sb = new StringBuilder();
            for (byte item : b) {
                i = item;
                if (i < 0)
                    i += 256;
                if (i < 16)
                    sb.append("0");
                sb.append(Integer.toHexString(i));
            }
            re_md5 = sb.toString();
        } catch (Exception e) {
            this.gtlog("md5_encode(): 发生异常, " + e.toString());
        }
        return re_md5;
    }

    /**
     * sha256加密
     */
    public String sha256_encode(String value) {
        MessageDigest messageDigest;
        String encodeStr = "";
        try {
            messageDigest = MessageDigest.getInstance("SHA-256");
            messageDigest.update(value.getBytes(StandardCharsets.UTF_8));
            encodeStr = byte2Hex(messageDigest.digest());
        } catch (Exception e) {
            this.gtlog("sha256_encode(): 发生异常, " + e.toString());
        }
        return encodeStr;
    }

    /**
     * 将byte转为16进制
     */
    private static String byte2Hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        String temp;
        for (byte aByte : bytes) {
            temp = Integer.toHexString(aByte & 0xFF);
            if (temp.length() == 1) {
                // 得到一位的进行补0操作
                sb.append("0");
            }
            sb.append(temp);
        }
        return sb.toString();
    }

    /**
     * hmac-sha256 加密
     */
    private String hmac_sha256_encode(String value, String key) {
        String encodeStr = "";
        try {
            Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
            SecretKeySpec secret_key = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256");
            sha256_HMAC.init(secret_key);
            byte[] array = sha256_HMAC.doFinal(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte item : array) {
                sb.append(Integer.toHexString((item & 0xFF) | 0x100).substring(1, 3));
            }
            encodeStr = sb.toString();
        } catch (Exception e) {
            this.gtlog("hmac_sha256_encode(): 发生异常, " + e.toString());
        }
        return encodeStr;
    }

}
