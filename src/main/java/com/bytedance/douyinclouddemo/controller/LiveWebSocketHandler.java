package com.bytedance.douyinclouddemo.controller;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class LiveWebSocketHandler extends TextWebSocketHandler {

    // anchorOpenID -> Session
    private final ConcurrentHashMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        // 客户端连接时，可以通过 URL 参数传 anchorOpenID，比如 ws://host/live_ws?anchorOpenID=123

        String anchorOpenID = session.getUri().getQuery().split("anchorOpenID=")[1];

        log.info("WebanchorOpenID: {}",anchorOpenID);

        if (anchorOpenID == null) {
            System.out.println("连接错误");
            session.close();

        } else {
            System.out.println("主播连接: " + anchorOpenID);
            sessions.put(anchorOpenID, session);
        }



        //  System.out.println("主播连接: " + anchorOpenID);
         String welcomeMessage = "连接成功，anchorOpenID：" + anchorOpenID;
         session.sendMessage(new TextMessage(welcomeMessage));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessions.values().remove(session);
    }

    // 通过 ROOMID 推送消息
    public void sendMessage(String anchorOpenID, String message) {
        WebSocketSession session = sessions.get(anchorOpenID);
        if (session != null && session.isOpen()) {
            try {
                session.sendMessage(new TextMessage(message));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
