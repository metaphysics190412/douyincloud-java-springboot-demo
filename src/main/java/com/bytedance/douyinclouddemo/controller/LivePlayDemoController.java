package com.bytedance.douyinclouddemo.controller;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import com.bytedance.douyinclouddemo.model.JsonResponse;
import com.bytedance.douyinclouddemo.model.LiveDataModel;
import com.bytedance.douyinclouddemo.model.LivePlayAPIResponse;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import lombok.Data;
import java.util.*;


//9-12

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
/**
 * 抖音云x弹幕玩法的服务端demo展示
 */
@RestController
@Slf4j
public class LivePlayDemoController {


    @Data
    public  static class  GameRequest {
        private String anchor_open_id;
        private String gameTime;
        private String isGameFinish;
        private List<Audience> audiences;

        @Data
        public  static class Audience {
            private String sec_openid;
            private String avatar_url;
            private String nickname;
            private int score;
            private int kill_num;
            private int win_num;
        }
    }

    //使能够调用WEBSOCKET发送信息
    private final LiveWebSocketHandler liveWebSocketHandler;
    public LivePlayDemoController(LiveWebSocketHandler liveWebSocketHandler) {
        this.liveWebSocketHandler = liveWebSocketHandler;
    }
    private static String accessToken;     // 缓存的 token
    private static long expireTime = 0;    // 过期时间戳（毫秒）
    //private static final OkHttpClient client = new OkHttpClient();
    /**
     * 开始玩法对局，玩法开始前调用
     */
    @PostMapping(path = "/start_game")
    public String callContainerExample(HttpServletRequest httpRequest) {
        // token
        String tokenFromExe = httpRequest.getHeader("Authorization");
        String appID = "tt2a485cf5b544e6eb10";
        String secret = "08211ae1e5cfb40a7768f8dc9063f5d2babe9e90";

        long now = System.currentTimeMillis();
        if (accessToken == null || now >= expireTime) {
            String result = GetAccessToken(appID, secret);
            // 解析成 JSONObject
            JSONObject jsonObject = JSON.parseObject(result);
            // 获取顶层字段
            int errNo = jsonObject.getIntValue("err_no");
            String errTips = jsonObject.getString("err_tips");

            // 获取 data 对象
            JSONObject dataObj = jsonObject.getJSONObject("data");
            accessToken = dataObj.getString("access_token");
            int expiresIn = dataObj.getIntValue("expires_in");
            expireTime = System.currentTimeMillis() + (expiresIn - 60) * 1000L; // 提前1分钟刷新
        }
        String resultinfo = GetRoomInfo(tokenFromExe);
        //  log.info("accessToken: {}, expireTime: {},token: {},", accessToken,expireTime,tokenFromExe);



        JSONObject json = JSON.parseObject(resultinfo);
        // 取 room_id
        String roomID  = json.getJSONObject("data")
                .getJSONObject("info")
                .getString("room_id");


        log.info("roomID: {}", roomID);


        // 调用弹幕玩法服务端API，开启直播间推送任务，开启后，开发者服务器会通过/live_data_callback接口 收到直播间玩法指令
        List<String> msgTypeList = new ArrayList<>();
        msgTypeList.add("live_like");
        msgTypeList.add("live_comment");
        msgTypeList.add("live_gift");
        msgTypeList.add("live_fansclub");

        for (String msgType : msgTypeList) {
            boolean result = startLiveDataTask(appID, roomID, msgType);
            if (result) {
                log.info("{} 推送开启成功", msgType);
            } else {
                log.error("{} 推送开启失败", msgType);
            }
        }
        return resultinfo;
    }




    /*
     * GetAccessToken: 获得访问TOKEN
     *
     * @param appID   小玩法appID
     * @param secret  加密ID
     */
    private String GetRoomInfo(String token) {
        // example: 通过java OkHttp库发起http请求,开发者可使用其余http访问形式
        OkHttpClient client = new OkHttpClient();
        // String body = token;

        String body = new JSONObject()
                .fluentPut("token", token)
                .toString();

        Request request = new Request.Builder()
                .url("https://webcast.bytedance.com/api/webcastmate/info") // accesstoken

                .addHeader("Content-Type", "application/json") // 无需维护access_token
                .addHeader("x-token", accessToken) // 无需维护access_token

                .post(
                        okhttp3.RequestBody.create(
                                MediaType.get("application/json; charset=utf-8"),
                                body
                        )
                )
                .build();


        try {
            Response httpResponse = client.newCall(request).execute();
            String rebody = httpResponse.body().string();
            return rebody;
        } catch (IOException e) {
            log.error("开启推送任务异常,e: {}", e.getMessage(), e);
            return "false";
        }

    }





    /**
     * GetAccessToken: 获得访问TOKEN
     *
     * @param appID   小玩法appID
     * @param secret  加密ID
     */
    private String GetAccessToken(String appID, String secret) {
        // example: 通过java OkHttp库发起http请求,开发者可使用其余http访问形式
        OkHttpClient client = new OkHttpClient();
        String body = new JSONObject()
                .fluentPut("appid", appID)
                .fluentPut("secret", secret)
                .fluentPut("grant_type", "client_credential")
                .toString();
        Request request = new Request.Builder()
                .url("https://developer.toutiao.com/api/apps/v2/token") // accesstoken

                .addHeader("Content-Type", "application/json") // 无需维护access_token
                .post(
                        okhttp3.RequestBody.create(
                                MediaType.get("application/json; charset=utf-8"),
                                body
                        )
                )
                .build();


        try {
            Response httpResponse = client.newCall(request).execute();
            String rebody = httpResponse.body().string();
            return rebody;
        } catch (IOException e) {
            log.error("开启推送任务异常,e: {}", e.getMessage(), e);
            return "false";
        }

    }
    //你需要改成自己的获取 token 地址和请求参数
    //private static final String TOKEN_URL = "https://developer.toutiao.com/api/apps/v2/token?appid=tt2a485cf5b544e6eb10&secret=08211ae1e5cfb40a7768f8dc9063f5d2babe9e90&grant_type=client_credential";
    /**
     * startLiveDataTask: 开启推送任务：<a href="https://developer.open-douyin.com/docs/resource/zh-CN/interaction/develop/server/live/danmu#%E5%90%AF%E5%8A%A8%E4%BB%BB%E5%8A%A1">...</a>
     *
     * @param appID   小玩法appID
     * @param roomID  直播间ID
     * @param msgType 评论/点赞/礼物/粉丝团
     */
    private boolean startLiveDataTask(String appID, String roomID, String msgType) {
        // example: 通过java OkHttp库发起http请求,开发者可使用其余http访问形式
        OkHttpClient client = new OkHttpClient();
        String body = new JSONObject()
                .fluentPut("roomid", roomID)
                .fluentPut("appid", appID)
                .fluentPut("msg_type", msgType)
                .toString();
        Request request = new Request.Builder()
                .url("http://webcast-bytedance-com.openapi.dyc.ivolces.com/api/live_data/task/start") // 使用webcast.bytedance.com的抖音云内网专线域名访问小玩法openAPI,无需https协议
                .addHeader("Content-Type", "application/json") // 无需维护access_token
                .post(
                        okhttp3.RequestBody.create(
                                MediaType.get("application/json; charset=utf-8"),
                                body
                        )
                )
                .build();

        try {
            Response httpResponse = client.newCall(request).execute();
            if (httpResponse.code() != 200) {
                log.error("开启推送任务失败,http访问非200");
                return false;
            }
            LivePlayAPIResponse livePlayAPIResponse
                    = JSON.parseObject(httpResponse.body().string(), LivePlayAPIResponse.class);
            if (livePlayAPIResponse.getErrNo() != 0) {
                log.error("开启推送任务失败，错误信息: {}", livePlayAPIResponse.getErrorMsg());
                return false;
            }
        } catch (IOException e) {
            log.error("开启推送任务异常,e: {}", e.getMessage(), e);
            return false;
        }
        return true;
    }

    /**
     * 结束玩法
     */
    @PostMapping(path = "/finish_game")
    public JsonResponse finishGameExample(HttpServletRequest httpRequest) {
        // TODO: 玩法对局结束,开发者自行实现对局结束逻辑
        JsonResponse response = new JsonResponse();
        response.success("finish");
        return response;
    }







    @Autowired
    private StringRedisTemplate stringRedisTemplate;  // key/value 字符串
    @Autowired
    private RedisTemplate<Object, Object> redisTemplate; // 可存对象
    @Autowired
    private ObjectMapper objectMapper;
    @PostMapping(path = "/rank_game")
    public String uploadGameData(@RequestBody GameRequest request) throws Exception {

        // 1. 获取 anchorId
        String anchorId = request.getAnchor_open_id();

        if (anchorId==null){return "error"; }
        System.out.println("anchorId: " + anchorId);
        // 2. 保存房间信息
        String roomInfoKey = "set_rank:" + anchorId + ":info";

        String IsFinish = request.getIsGameFinish();
        System.out.println(request.getIsGameFinish());
        //IsFinish = "true";
        stringRedisTemplate.opsForHash().put(roomInfoKey, "isFinish", IsFinish);

        String GameTime = request.getGameTime ();
        System.out.println(GameTime);
        stringRedisTemplate.opsForHash().put(roomInfoKey, "gameTime", GameTime);
        System.out.println("startTime: " + request.getGameTime());

        // 3. 清空旧的玩家数据
        String playerKey = "set_rank:" + anchorId + ":players";
        stringRedisTemplate.delete(playerKey);
        System.out.println(request.getIsGameFinish());
        // 4. 保存新的玩家数据
        for (GameRequest.Audience audience : request.getAudiences()) {
            String value = objectMapper.writeValueAsString(audience); // audience 转 JSON

            if (IsFinish == "true" ){
                System.out.println(IsFinish);
                saveOrUpdateDayRank(value);
            }else{
                stringRedisTemplate.opsForHash().put(playerKey, audience.getSec_openid(), value);
            }
        }

        if (IsFinish == "true" ){
            generateWeekRank(1);
            generateMonthRank();
        }
        //String Rs =getMonthRankTopN(50);
        return encrypt("success","1111111111111111");
    }
    //生成
    // deleteRoomData(anchorId,"set_rank");
    //String Rs=getTopNRankString(anchorId,"set_rank",1000);
    //删除局信息
    public void deleteRoomData(String anchorId, String rankPrefix) {
        // 1. 构建 key
        String roomInfoKey = rankPrefix + ":" + anchorId + ":info";
        String playerKey = rankPrefix + ":" + anchorId + ":players";

        // 2. 删除 key
        stringRedisTemplate.delete(roomInfoKey);
        stringRedisTemplate.delete(playerKey);

        System.out.println("已删除 Redis 数据：");
        System.out.println("roomInfoKey -> " + roomInfoKey);
        System.out.println("playerKey -> " + playerKey);
    }
    //导出局排名
    public String getTopNRankString(String anchorId, String rankPrefix, int topN) throws Exception {
        // 1. 构建 Redis key
        String playerKey = rankPrefix + ":" + anchorId + ":players";

        // 2. 获取 Redis 中所有玩家数据
        Map<Object, Object> playersMap = stringRedisTemplate.opsForHash().entries(playerKey);
        if (playersMap.isEmpty()) {
            return "rank_class:" + rankPrefix + " -> []"; // 没有玩家
        }

        // 3. 反序列化成 Audience 对象列表
        List<GameRequest.Audience> players = new ArrayList<>();
        for (Object value : playersMap.values()) {
            String json = (String) value;
            GameRequest.Audience audience = objectMapper.readValue(json, GameRequest.Audience.class);
            players.add(audience);
        }

        // 4. 按 score 降序排序
        players.sort((a, b) -> Integer.compare(b.getScore(), a.getScore()));

        // 5. 取前 topN 名
        int limit = Math.min(topN, players.size());
        List<GameRequest.Audience> topPlayers = players.subList(0, limit);

        // 6. 拼接成字符串输出，并加上 rank
        StringBuilder sb = new StringBuilder();
        sb.append("rank_class:").append(rankPrefix).append(" -> [");

        for (int i = 0; i < topPlayers.size(); i++) {
            GameRequest.Audience p = topPlayers.get(i);
            Map<String, Object> playerInfo = new HashMap<>();
            playerInfo.put("rank", i + 1);              // 排名
            playerInfo.put("sec_openid", p.getSec_openid());
            playerInfo.put("nickname", p.getNickname());
            playerInfo.put("score", p.getScore());
            playerInfo.put("kill_num", p.getKill_num());
            playerInfo.put("win_num", p.getWin_num());

            sb.append(objectMapper.writeValueAsString(playerInfo));
            if (i != topPlayers.size() - 1) {
                sb.append(", ");
            }
        }

        sb.append("]");
        return sb.toString();
    }
    //保存日积分
    public void generateWeekRank(int targetWeekday) throws Exception {
        // 1. 获取今天日期
        LocalDate today = LocalDate.now();

        // 2. 找到最近的目标星期几
        LocalDate startDate = today;
        while (startDate.getDayOfWeek().getValue() != targetWeekday) {
            startDate = startDate.minusDays(1);
        }

        System.out.println("周排行起始日期: " + startDate);

        // 3. 遍历从 startDate 到今天的日期
        Map<String, GameRequest.Audience> aggregated = new HashMap<>();

        for (LocalDate date = startDate; !date.isAfter(today); date = date.plusDays(1)) {
            String dayKey = "day_rank:" + date.format(DateTimeFormatter.BASIC_ISO_DATE);
            Map<Object, Object> dayData = stringRedisTemplate.opsForHash().entries(dayKey);

            for (Object objValue : dayData.values()) {
                String json = (String) objValue;
                GameRequest.Audience audience = objectMapper.readValue(json, GameRequest.Audience.class);
                String secOpenId = audience.getSec_openid();

                if (aggregated.containsKey(secOpenId)) {
                    GameRequest.Audience existing = aggregated.get(secOpenId);
                    // score 累加
                    existing.setScore(existing.getScore() + audience.getScore());
                    // kill_num 取最大
                    existing.setKill_num(Math.max(existing.getKill_num(), audience.getKill_num()));
                    // win_num 也可以取最大或累加，根据规则，我假设累加
                    existing.setWin_num(existing.getWin_num() + audience.getWin_num());
                    // 其他信息取最新（今天的数据）
                    existing.setNickname(audience.getNickname());
                    existing.setAvatar_url(audience.getAvatar_url());
                    // 更新回 map
                    aggregated.put(secOpenId, existing);
                } else {
                    // 新玩家直接放入
                    aggregated.put(secOpenId, audience);
                }
            }
        }

        // 4. 保存到 week_rank:{startDate} 表
        String weekKey = "week_rank:" + startDate.format(DateTimeFormatter.BASIC_ISO_DATE);
        for (Map.Entry<String, GameRequest.Audience> entry : aggregated.entrySet()) {
            String secOpenId = entry.getKey();
            String json = objectMapper.writeValueAsString(entry.getValue());
            stringRedisTemplate.opsForHash().put(weekKey, secOpenId, json);
        }

        System.out.println("周排行表已生成: " + weekKey);
    }
    public void generateMonthRank() throws Exception {
        // 1. 获取今天日期和本月第一天
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.withDayOfMonth(1);
        System.out.println("月排行起始日期: " + startDate);
        // 2. 用一个 Map 累计每个 sec_openid 的数据
        Map<String, GameRequest.Audience> aggregated = new HashMap<>();

        // 3. 遍历从 startDate 到今天的日期
        for (LocalDate date = startDate; !date.isAfter(today); date = date.plusDays(1)) {
            String dayKey = "day_rank:" + date.format(DateTimeFormatter.BASIC_ISO_DATE);
            Map<Object, Object> dayData = stringRedisTemplate.opsForHash().entries(dayKey);

            for (Object objValue : dayData.values()) {
                String json = (String) objValue;
                GameRequest.Audience audience = objectMapper.readValue(json, GameRequest.Audience.class);
                String secOpenId = audience.getSec_openid();

                if (aggregated.containsKey(secOpenId)) {
                    GameRequest.Audience existing = aggregated.get(secOpenId);
                    // score 累加
                    existing.setScore(existing.getScore() + audience.getScore());
                    // kill_num 取最大
                    existing.setKill_num(Math.max(existing.getKill_num(), audience.getKill_num()));
                    // win_num 累加
                    existing.setWin_num(existing.getWin_num() + audience.getWin_num());
                    // 其他字段取最新数据
                    existing.setNickname(audience.getNickname());
                    existing.setAvatar_url(audience.getAvatar_url());

                    aggregated.put(secOpenId, existing);
                } else {
                    // 新玩家直接放入
                    aggregated.put(secOpenId, audience);
                }
            }
        }

        // 4. 保存到 month_rank:{startDate} 表
        String monthKey = "month_rank:" + startDate.format(DateTimeFormatter.BASIC_ISO_DATE);
        for (Map.Entry<String, GameRequest.Audience> entry : aggregated.entrySet()) {
            String secOpenId = entry.getKey();
            String json = objectMapper.writeValueAsString(entry.getValue());
            stringRedisTemplate.opsForHash().put(monthKey, secOpenId, json);
        }

        System.out.println("月排行表已生成: " + monthKey);
    }
    public void saveOrUpdateDayRank(String value) throws Exception {
        // 1. 将传入 value 反序列化成 Audience 对象
        GameRequest.Audience newAudience = objectMapper.readValue(value, GameRequest.Audience.class);
        String secOpenId = newAudience.getSec_openid();

        // 2. 获取当天日期
        String today = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);

        // 3. 构建 Redis key
        String dayRankKey = "day_rank:" + today;

        // 4. 获取当天 Hash 所有数据
        Map<Object, Object> allPlayers = stringRedisTemplate.opsForHash().entries(dayRankKey);

        boolean found = false;

        // 5. 遍历 Hash，找到 sec_openid 相同的记录
        for (Map.Entry<Object, Object> entry : allPlayers.entrySet()) {
            String json = (String) entry.getValue();
            GameRequest.Audience existing = objectMapper.readValue(json, GameRequest.Audience.class);

            if (existing.getSec_openid().equals(secOpenId)) {
                // score 相加
                existing.setScore(existing.getScore() + newAudience.getScore());
                // kill_num 取最大
                existing.setKill_num(Math.max(existing.getKill_num(), newAudience.getKill_num()));
                // win_num 判断
                if (existing.getWin_num() == 0 || newAudience.getWin_num() == 0) {
                    existing.setWin_num(0);
                } else {
                    existing.setWin_num(existing.getWin_num() + newAudience.getWin_num());
                }
                // 其他信息取最新
                existing.setNickname(newAudience.getNickname());
                existing.setAvatar_url(newAudience.getAvatar_url());

                // 写回 Redis
                String updatedJson = objectMapper.writeValueAsString(existing);
                stringRedisTemplate.opsForHash().put(dayRankKey, secOpenId, updatedJson);

                found = true;
                break;
            }
        }

        // 6. 如果没找到记录，直接写入
        if (!found) {
            stringRedisTemplate.opsForHash().put(dayRankKey, secOpenId, value);
        }

        System.out.println("更新/写入完成: " + dayRankKey + " -> " + secOpenId);
    }
    public String getMonthRankTopN(int topN) throws Exception {
        // 1. 生成表名
        LocalDate today = LocalDate.now();
        String tableName = "month_rank:" + today.format(DateTimeFormatter.ofPattern("yyyyMM")) + "01";

        // 2. 查询表数据
        Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(tableName);

        // 3. 解析JSON成对象
        List<Audience> players = new ArrayList<>();
        for (Object value : entries.values()) {
            String json = value.toString();
            Audience audience = objectMapper.readValue(json, Audience.class);
            players.add(audience);
        }

        // 4. 排序 + 截取前N
        List<Audience> topPlayers = players.stream()
                .sorted(Comparator.comparingInt(Audience::getScore).reversed())
                .limit(topN)
                .collect(Collectors.toList());

        // 5. 转JSON字符串返回
        return objectMapper.writeValueAsString(topPlayers);
    }
    public static class Audience {
        private String sec_openid;
        private String avatar_url;
        private String nickname;
        private int score;
        private int kill_num;
        private int win_num;

        public String getSec_openid() { return sec_openid; }
        public void setSec_openid(String sec_openid) { this.sec_openid = sec_openid; }
        public String getAvatar_url() { return avatar_url; }
        public void setAvatar_url(String avatar_url) { this.avatar_url = avatar_url; }
        public String getNickname() { return nickname; }
        public void setNickname(String nickname) { this.nickname = nickname; }
        public int getScore() { return score; }
        public void setScore(int score) { this.score = score; }
        public int getKill_num() { return kill_num; }
        public void setKill_num(int kill_num) { this.kill_num = kill_num; }
        public int getWin_num() { return win_num; }
        public void setWin_num(int win_num) { this.win_num = win_num; }
    }
    /**
     * 通过抖音云服务接受直播间数据，内网专线加速+免域名备案
     * 通过内网专线会自动携带X-Anchor-OpenID字段
     * ref: <a href="https://developer.open-douyin.com/docs/resource/zh-CN/developer/tools/cloud/develop-guide/danmu-callback">...</a>
     */

    private static final String ALGORITHM = "AES/ECB/PKCS5Padding";

    // 解密
    public static String decrypt(String encryptedData, String key) throws Exception {
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes("UTF-8"), "AES");
        cipher.init(Cipher.DECRYPT_MODE, secretKeySpec);
        byte[] decoded = Base64.getDecoder().decode(encryptedData);
        byte[] original = cipher.doFinal(decoded);
        return new String(original, "UTF-8");
    }

    // 加密（方便你测试）
    public static String encrypt(String data, String key) throws Exception {
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes("UTF-8"), "AES");
        cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec);
        byte[] encrypted = cipher.doFinal(data.getBytes("UTF-8"));
        return Base64.getEncoder().encodeToString(encrypted);
    }



    @PostMapping(path = "/live_data_callback")
    public JsonResponse liveDataCallbackExample(
            @RequestHeader("X-Anchor-OpenID") String anchorOpenID,
            @RequestHeader("x-msg-type") String msgType,
            @RequestBody String body) {
        List<LiveDataModel> liveDataModelList = JSON.parseArray(body, LiveDataModel.class);
        liveDataModelList.forEach(liveDataModel ->
                pushDataToClientByDouyinCloudWebsocket(anchorOpenID, liveDataModel.getMsgID(), msgType, body)

        );

        liveWebSocketHandler.sendMessage(anchorOpenID, body);
        log.info("body: {},callbackanchorOpenID: {}", body,anchorOpenID);

        JsonResponse response = new JsonResponse();
        response.success("success");
        return response;
    }


    //---------------- 抖音云websocket相关demo ---------------------

    /**
     * 抖音云websocket监听的回调函数,客户端建连/上行发消息都会走到该HTTP回调函数中
     * ref: <a href="https://developer.open-douyin.com/docs/resource/zh-CN/developer/tools/cloud/develop-guide/websocket-guide/websocket#%E5%BB%BA%E8%BF%9E%E8%AF%B7%E6%B1%82">...</a>
     */
    @RequestMapping(path = "/websocket_callback", method = {RequestMethod.POST, RequestMethod.GET})
    public JsonResponse websocketCallback(HttpServletRequest request) {
        String eventType = request.getHeader("x-tt-event-type");
        switch (eventType) {
            case "connect":
                // 客户端建连
            case "disconnect": {
                // 客户端断连
            }
            case "uplink": {
                // 客户端上行发消息
            }
            default:
                break;
        }
        JsonResponse response = new JsonResponse();
        response.success("success");
        return response;
    }

    /**
     * 使用抖音云websocket网关,将数据推送到主播端
     * ref: <a href="https://developer.open-douyin.com/docs/resource/zh-CN/developer/tools/cloud/develop-guide/websocket-guide/websocket#%E4%B8%8B%E8%A1%8C%E6%B6%88%E6%81%AF%E6%8E%A8%E9%80%81">...</a>
     */
    private void pushDataToClientByDouyinCloudWebsocket(String anchorOpenId, String msgID, String msgType, String data) {
        // 这里通过HTTP POST请求将数据推送给抖音云网关,进而抖音云网关推送给主播端
        OkHttpClient client = new OkHttpClient();

        Map<String, String> bodyMap = new HashMap<>();
        bodyMap.put("msg_id", msgID);
        bodyMap.put("msg_type", msgType);
        bodyMap.put("data", data);
//        bodyMap.put("extra_data", "");
        String bodyStr = JSON.toJSONString(bodyMap);

        Request request = new Request.Builder()
                .url("http://ws-push.dyc.ivolces.com/ws/live_interaction/push_data")
                .addHeader("Content-Type", "application/json")
                .addHeader("X-TT-WS-OPENIDS", JSON.toJSONString(Arrays.asList(anchorOpenId)))
                .post(
                        okhttp3.RequestBody.create(
                                MediaType.parse("application/json; charset=utf-8"),
                                bodyStr
                        )
                )
                .build();

        try {
            Response httpResponse = client.newCall(request).execute();
            log.info("websocket http call done, response: {}", JSON.toJSONString(httpResponse));
        } catch (IOException e) {
            log.error("websocket http call exception, e: ", e);
        }
    }
}
