package io.takamiya.mio.ws.feature;

import org.json.JSONException;
import org.json.JSONObject;

import cn.panda.utils.library.StringUtils;

public class TestCommand extends WsFeature {

    public TestCommand(String key, String name, String desc) {
        super(key, name, desc);
    }

    @Override
    public void onPlayerMessage(String json) {
        // {"message":"qaa","receiver":"","sender":"AIR114514CH","type":"chat"}
        try {
            JSONObject msgObj = new JSONObject(json);
            String message = msgObj.optString("message");
            String sender = msgObj.optString("sender");
            String type = msgObj.optString("type");
            if ("chat".equals(type) && message.startsWith("$") && message.length() > 1) {
                String command = message.substring(1);
                if (!command.isEmpty()) {
                    sendCommand(command);
                    sendTell(sender, "命令已执行");
                }
            }
        } catch (JSONException e) {
            showToast("消息解析失败");
        }
    }
}
