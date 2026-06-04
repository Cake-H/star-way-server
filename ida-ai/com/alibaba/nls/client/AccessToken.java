/*
 * Copyright 2015 Alibaba Group Holding Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nls.client;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.SimpleTimeZone;
import java.util.UUID;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.bind.DatatypeConverter;

/**
 * 访问令牌是用户访问智能语音服务的凭证
 *
 * @author xuebin
 */
public class AccessToken {

    private static final String NLS_DOMAIN = "nls-meta.cn-shanghai.aliyuncs.com";

    private static final String NLS_VERSION = "2019-02-28";

    private static final String MCOS_DOMAIN = "mcos.cn-shanghai.aliyuncs.com";

    private static final String MCOS_VERSION = "2022-08-11";

    private final static String TIME_ZONE = "GMT";

    private final static String FORMAT_ISO8601 = "yyyy-MM-dd'T'HH:mm:ss'Z'";

    private final static String URL_ENCODING = "UTF-8";

    private static final String ALGORITHM_NAME = "HmacSHA1";

    private static final String ENCODING = "UTF-8";

    private static Logger logger = LoggerFactory.getLogger(AccessToken.class);

    private String accessKeyId;

    private String accessKeySecret;

    private String domain = "";

    private String regionId = "cn-shanghai";

    private String version = "";

    private String action = "CreateToken";

    private String token;

    private long expireTime;

    /**
     * 构造实例
     *
     * @param accessKeyId     阿里云akid
     * @param accessKeySecret 阿里云secret key
     */
    public AccessToken(String accessKeyId, String accessKeySecret) {
        this.accessKeyId = accessKeyId;
        this.accessKeySecret = accessKeySecret;
        domain = NLS_DOMAIN;
        version = NLS_VERSION;
    }

    public AccessToken(String accessKeyId, String accessKeySecret, AccessTokenType type) {
        this.accessKeyId = accessKeyId;
        this.accessKeySecret = accessKeySecret;
        if (AccessTokenType.MCOS.equals(type)) {
            domain = MCOS_DOMAIN;
            version = MCOS_VERSION;
        } else {
            domain = NLS_DOMAIN;
            version = NLS_VERSION;
        }
    }

    /**
     * @param accessKeyId     阿里云akid
     * @param accessKeySecret 阿里云secret key
     * @param domain          服务域名
     * @param regionId        服务的地域ID
     * @param version         API的版本
     */
    public AccessToken(String accessKeyId, String accessKeySecret,
                       String domain, String regionId, String version) {
        this.accessKeyId = accessKeyId;
        this.accessKeySecret = accessKeySecret;
        this.domain = domain;
        this.regionId = regionId;
        this.version = version;
    }

    /**
     * 获取时间戳 必须符合ISO8601规范，并需要使用UTC时间，时区为+0
     */
    private String getISO8601Time(Date date) {
        Date nowDate = date;
        if (null == date) {
            nowDate = new Date();
        }
        SimpleDateFormat df = new SimpleDateFormat(FORMAT_ISO8601);
        df.setTimeZone(new SimpleTimeZone(0, TIME_ZONE));
        return df.format(nowDate);
    }

    /**
     * 获取UUID
     */
    private String getUniqueNonce() {
        UUID uuid = UUID.randomUUID();
        return uuid.toString();
    }

    /**
     * URL编码 使用UTF-8字符集按照 RFC3986 规则编码请求参数和参数取值
     */
    private String percentEncode(String value) throws UnsupportedEncodingException {
        return value != null ? URLEncoder.encode(value, URL_ENCODING).replace("+", "%20")
            .replace("*", "%2A").replace("%7E", "~") : null;
    }

    /***
     * 将参数排序后，进行规范化设置，组合成请求字符串
     * @param queryParamsMap   所有请求参数
     * @return 规范化的请求字符串
     */
    private String canonicalizedQuery(Map<String, String> queryParamsMap) {
        String[] sortedKeys = queryParamsMap.keySet().toArray(new String[] {});
        Arrays.sort(sortedKeys);
        String queryString = null;
        try {
            StringBuilder canonicalizedQueryString = new StringBuilder();
            for (String key : sortedKeys) {
                canonicalizedQueryString.append("&")
                    .append(percentEncode(key)).append("=")
                    .append(percentEncode(queryParamsMap.get(key)));
            }
            queryString = canonicalizedQueryString.toString().substring(1);
        } catch (UnsupportedEncodingException e) {
            logger.error("UTF-8 encoding is not supported.");
            e.printStackTrace();
        }
        return queryString;
    }

    /***
     * 构造签名字符串
     * @param method       HTTP请求的方法
     * @param urlPath      HTTP请求的资源路径
     * @param queryString  规范化的请求字符串
     * @return 签名字符串
     */
    private String createStringToSign(String method, String urlPath, String queryString) {
        String stringToSign = null;
        try {
            StringBuilder strBuilderSign = new StringBuilder();
            strBuilderSign.append(method);
            strBuilderSign.append("&");
            strBuilderSign.append(percentEncode(urlPath));
            strBuilderSign.append("&");
            strBuilderSign.append(percentEncode(queryString));
            stringToSign = strBuilderSign.toString();
        } catch (UnsupportedEncodingException e) {
            logger.error("UTF-8 encoding is not supported.");
            e.printStackTrace();
        }
        return stringToSign;
    }

    /***
     * 计算签名
     * @param stringToSign      签名字符串
     * @param accessKeySecret   阿里云AccessKey Secret加上与号&
     * @return 计算得到的签名
     */
    private String sign(String stringToSign, String accessKeySecret) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM_NAME);
            mac.init(new SecretKeySpec(
                accessKeySecret.getBytes(ENCODING),
                ALGORITHM_NAME
            ));
            byte[] signData = mac.doFinal(stringToSign.getBytes(ENCODING));
            String signBase64 = DatatypeConverter.printBase64Binary(signData);
            String signUrlEncode = percentEncode(signBase64);
            return signUrlEncode;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException(e.toString());
        } catch (UnsupportedEncodingException e) {
            throw new IllegalArgumentException(e.toString());
        } catch (InvalidKeyException e) {
            throw new IllegalArgumentException(e.toString());
        }
    }

    /***
     * 发送HTTP GET请求，获取token和有效期时间戳
     * @param queryString 请求参数
     */
    private void processGETRequest(String queryString) {
        /**
         * 设置HTTP GET请求
         * 1. 使用HTTP协议
         * 2. Token服务域名
         * 3. 请求路径：/
         * 4. 设置请求参数
         */
        String url = "http://" + this.domain;
        url = url + "/";
        url = url + "?" + queryString;
        Request request = new Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .get()
            .build();
        try {
            OkHttpClient client = new OkHttpClient();
            Response response = client.newCall(request).execute();
            String result = response.body().string();
            if (response.isSuccessful()) {
                JSONObject rootObj = JSON.parseObject(result);
                JSONObject tokenObj = rootObj.getJSONObject("Token");
                if (tokenObj != null) {
                    this.token = tokenObj.getString("Id");
                    this.expireTime = tokenObj.getLongValue("ExpireTime");
                } else {
                    logger.error("Create the token failed: " + result);
                }
            } else {
                System.err.println("Create the token failed: " + result);
            }
            response.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 向服务端申请访问令牌，调用即返回，任务在后台运行，申请成功后会更新token和expireTime
     *
     * @throws IOException https调用出错
     */
    public void apply() throws IOException {
        Map<String, String> queryParamsMap = new HashMap<String, String>();
        queryParamsMap.put("AccessKeyId", this.accessKeyId);
        queryParamsMap.put("Action", this.action);
        queryParamsMap.put("Version", this.version);
        queryParamsMap.put("RegionId", this.regionId);
        queryParamsMap.put("Timestamp", getISO8601Time(null));
        queryParamsMap.put("Format", "JSON");
        queryParamsMap.put("SignatureMethod", "HMAC-SHA1");
        queryParamsMap.put("SignatureVersion", "1.0");
        queryParamsMap.put("SignatureNonce", getUniqueNonce());

        String queryString = canonicalizedQuery(queryParamsMap);
        if (null == queryString) {
            logger.error("create the canonicalized query failed");
            return;
        }

        String method = "GET";
        String urlPath = "/";
        String stringToSign = createStringToSign(method, urlPath, queryString);
        if (null == stringToSign) {
            logger.error("create the sign string failed");
            return;
        }

        String signature = sign(stringToSign, accessKeySecret + "&");
        if (null == signature) {
            logger.error("computer the sign failed!");
            return;
        }
        String queryStringWithSign = "Signature=" + signature + "&" + queryString;
        processGETRequest(queryStringWithSign);
    }

    public String getToken() {
        return token;
    }

    public long getExpireTime() {
        return expireTime;
    }

}
