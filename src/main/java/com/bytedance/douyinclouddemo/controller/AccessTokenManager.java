import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import java.io.IOException;

/**
 * 抖音直播小游戏 access_token 管理工具类
 */
public class AccessTokenManager {
    private static String accessToken;     // 缓存的 token
    private static long expireTime = 0;    // 过期时间戳（毫秒）
    private static final OkHttpClient client = new OkHttpClient();

    //你需要改成自己的获取 token 地址和请求参数
    private static final String TOKEN_URL = "https://developer.toutiao.com/api/apps/v2/token?appid=tt2a485cf5b544e6eb10&secret=08211ae1e5cfb40a7768f8dc9063f5d2babe9e90&grant_type=client_credential";
             
    
    
    //String appID = "tt2a485cf5b544e6eb10";
    // String secret = "08211ae1e5cfb40a7768f8dc9063f5d2babe9e90";
    /**
     * 获取有效的 access_token
     */

    
    public static synchronized String getAccessToken() {
        long now = System.currentTimeMillis();
        if (accessToken == null || now >= expireTime) {
            // token 为空 或 已过期 -> 重新请求
           // refreshToken();
            return "null";
        }
        return "accessToken";
    }
    
    /**
     * 请求抖音接口，刷新 access_token
     */

 
    private static void refreshToken() {
        try (Response httpResponse = client.newCall(
                new Request.Builder().url(TOKEN_URL).get().build()
        ).execute()) {
            String body = httpResponse.body().string();
          //  System.out.println("刷新 token 返回: " + body);

            JSONObject json = JSON.parseObject(body);
            int errNo = json.getIntValue("err_no");

            if (errNo == 0) {
                JSONObject data = json.getJSONObject("data");
                accessToken = data.getString("access_token");
                int expiresIn = data.getIntValue("expires_in"); // 单位: 秒
                expireTime = System.currentTimeMillis() + (expiresIn - 60) * 1000L; // 提前1分钟刷新
            } else {
               // System.err.println("获取 access_token 失败: " + json.getString("err_tips"));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }  
}
