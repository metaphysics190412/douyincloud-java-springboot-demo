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
import java.util.*;

/**
 * 抖音云x弹幕玩法的服务端demo展示
 */
@RestController
@Slf4j
public class LivePlayDemoController {

    /**
     * 开始玩法对局，玩法开始前调用
     */
    @PostMapping(path = "/start_game")
    public JsonResponse callContainerExample(HttpServletRequest httpRequest) {
        // 开发者可以直接通过请求头获取直播间信息,无需自行通过token置换

        // 应用id
        String appID = httpRequest.getHeader("X-TT-AppID");
        // 直播间id
        String roomID = httpRequest.getHeader("X-Room-ID");
        // 主播id
        String anchorOpenID = httpRequest.getHeader("X-Anchor-OpenID");
        // 主播头像url
        String avatarUrl = httpRequest.getHeader("X-Avatar-Url");
        // 主播昵称
        String nickName = httpRequest.getHeader("X-Nick-Name");

        log.info("appID: {}, roomID: {}, anchorOpenID: {}, avatarUrl: {}, nickName: {}", appID,
                roomID, anchorOpenID, avatarUrl, nickName);


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

        JsonResponse response = new JsonResponse();
        response.success("开始玩法对局成功");
        return response;
    }

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
        response.success("结束玩法成功");
        return response;
    }


    /**
     * 通过抖音云服务接受直播间数据，内网专线加速+免域名备案
     * 通过内网专线会自动携带X-Anchor-OpenID字段
     * ref: <a href="https://developer.open-douyin.com/docs/resource/zh-CN/developer/tools/cloud/develop-guide/danmu-callback">...</a>
     */
    @PostMapping(path = "/live_data_callback")
    public JsonResponse liveDataCallbackExample(
            @RequestHeader("X-Anchor-OpenID") String anchorOpenID,
            @RequestHeader("x-msg-type") String msgType,
            @RequestBody String body) {
        List<LiveDataModel> liveDataModelList = JSON.parseArray(body, LiveDataModel.class);
        liveDataModelList.forEach(liveDataModel ->
                pushDataToClientByDouyinCloudWebsocket(anchorOpenID, liveDataModel.getMsgID(), msgType, body)
        );
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
