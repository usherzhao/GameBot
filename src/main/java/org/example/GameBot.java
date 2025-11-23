package org.example;

import ai.djl.Model;
import ai.djl.engine.Engine;
import ai.djl.inference.Predictor;
import ai.djl.modality.Classifications;
import ai.djl.modality.cv.Image;
import ai.djl.modality.cv.ImageFactory;
import ai.djl.modality.cv.transform.Resize;
import ai.djl.modality.cv.transform.ToTensor;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDArray; // 记得保留这个
import ai.djl.nn.Block;
import ai.djl.nn.Blocks;
import ai.djl.nn.Activation;
import ai.djl.nn.SequentialBlock;
import ai.djl.nn.convolutional.Conv2d;
import ai.djl.nn.core.Linear;
import ai.djl.nn.pooling.Pool;
import ai.djl.ndarray.types.Shape;
import ai.djl.translate.Batchifier;
import ai.djl.translate.Pipeline;
import ai.djl.translate.Translator;
import ai.djl.translate.TranslatorContext;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import okhttp3.*; // 引入 OkHttp

import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class GameBot {

    public static class AppConfig {
        public String key;
        public Map<String, ButtonConfig> resolutions;
    }

    public static class ButtonConfig {
        public int x;
        public int y;
    }

    // --- 全局变量 ---
    private static AppConfig fullConfig;
    private static ButtonConfig currentButtonConfig;

    private static final String CONFIG_FILE_NAME = "config.json";
    private static final OkHttpClient client = new OkHttpClient();

    private static final String BASE_API_URL = "https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=";
    private static String fullApiUrl = null;

    // 逻辑控制变量
    private static long lastNotificationTime = 0;
    private static final long COOLDOWN = 5 * 60 * 1000;
    private static long lastClickTime = 0;
    private static final long CLICK_COOLDOWN = 5000;
    private static boolean loadConfig() {
        try {
            Path path = Paths.get(CONFIG_FILE_NAME);
            if (!Files.exists(path)) {
                System.err.println("错误: 未找到 " + CONFIG_FILE_NAME);
                return false;
            }
            String jsonContent = Files.readString(path);
            fullConfig = JSON.parseObject(jsonContent, GameBot.AppConfig.class);

            if (fullConfig == null || fullConfig.key == null || fullConfig.resolutions == null) {
                System.err.println("错误: JSON 格式不对，缺少 key 或 resolutions");
                return false;
            }

            fullApiUrl = BASE_API_URL + fullConfig.key;
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private static boolean matchResolution() {
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        int w = (int) screenSize.getWidth();
        int h = (int) screenSize.getHeight();
        String currentKey = w + "x" + h;
        System.out.println(">>> 检测到当前屏幕分辨率: " + currentKey);

        if (fullConfig.resolutions.containsKey(currentKey)) {
            currentButtonConfig = fullConfig.resolutions.get(currentKey);
            System.out.println(">>> 成功匹配配置！点击坐标: (" + currentButtonConfig.x + ", " + currentButtonConfig.y + ")");
            return true;
        } else {
            System.err.println(">>> 错误: 配置文件 config.json 中没有包含 [" + currentKey + "] 的配置！");
            return false;
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println(">>> 当前 DJL 库版本: " + Engine.getInstance().getVersion());
        // --- 第一步：启动时读取配置文件 ---
        if (!loadConfig()) {
            return;
        }

        // 2. 自动匹配分辨率
        if (!matchResolution()) {
            return;
        }
        List<String> classes = Arrays.asList("game", "login", "select");

        // ... (神经网络结构代码保持不变，为了节省篇幅省略，请保留原样) ...
        Block block = new SequentialBlock()
                .add(Conv2d.builder().setKernelShape(new Shape(3, 3)).setFilters(32).build())
                .add(Activation.reluBlock())
                .add(Pool.maxPool2dBlock(new Shape(2, 2)))
                .add(Conv2d.builder().setKernelShape(new Shape(3, 3)).setFilters(64).build())
                .add(Activation.reluBlock())
                .add(Pool.maxPool2dBlock(new Shape(2, 2)))
                .add(Conv2d.builder().setKernelShape(new Shape(3, 3)).setFilters(64).build())
                .add(Activation.reluBlock())
                .add(Pool.maxPool2dBlock(new Shape(2, 2)))
                .add(Blocks.batchFlattenBlock())
                .add(Linear.builder().setUnits(64).build())
                .add(Activation.reluBlock())
                .add(Linear.builder().setUnits(classes.size()).build());

        Translator<Image, Classifications> translator = new Translator<Image, Classifications>() {
            @Override
            public Classifications processOutput(TranslatorContext ctx, NDList list) {
                return new Classifications(classes, list.singletonOrThrow().softmax(0));
            }
            @Override
            public NDList processInput(TranslatorContext ctx, Image input) {
                NDArray array = input.toNDArray(ctx.getNDManager());
                NDList inputList = new NDList(array);
                Pipeline pipeline = new Pipeline();
                pipeline.add(new Resize(128, 128));
                pipeline.add(new ToTensor());
                return pipeline.transform(inputList);
            }
            @Override
            public Batchifier getBatchifier() {
                return Batchifier.STACK;
            }
        };

        System.out.println(">>> 正在加载 AI 模型...");

        try (Model model = Model.newInstance("game-classifier")) {
            model.setBlock(block);
            model.load(Paths.get("build/model"));

            try (Predictor<Image, Classifications> predictor = model.newPredictor(translator)) {
                Robot robot = new Robot();
                Rectangle screenRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());

                System.out.println(">>> 挂机助手已启动！");

                while (true) {
                    long loopStart = System.currentTimeMillis();

                    // 1. 截屏
                    BufferedImage bufImg = robot.createScreenCapture(screenRect);
                    Image img = ImageFactory.getInstance().fromImage(bufImg);

                    // 2. 识别
                    Classifications result = predictor.predict(img);
                    String status = result.best().getClassName();
                    double probability = result.best().getProbability();

                    System.out.println("当前状态: " + status + " (" + String.format("%.2f", probability) + ")");

                    // 3. 核心逻辑
                    if (probability > 0.8) {
                        // --- 新增逻辑：如果不是 game 状态，且冷却时间已过，就发送请求 ---
                        if (!"game".equals(status)) {
                            long currentTime = System.currentTimeMillis();
                            // 判断是否超过 5 分钟
                            if (currentTime - lastNotificationTime >= COOLDOWN) {
                                System.out.println("!!! 异常状态提醒：发送 Post 请求 !!!");
                                sendPostRequest(status); // 发送请求
                                lastNotificationTime = currentTime; // 更新发送时间
                            }
                        }

                        // 原有的业务处理
                        switch (status) {
                            case "game":
                                // 正常挂机
                                break;
                            case "login":
                                // 重连逻辑
                                break;
                            case "select":
                                // 选人逻辑
                                handleSelectLogic(robot);
                                break;
                        }
                    }

                    // 4. 休眠控制频率
                    long cost = System.currentTimeMillis() - loopStart;
                    if (cost < 1000) {
                        Thread.sleep(1000 - cost);
                    }
                }
            }
        }
    }

    // --- 新增：读取配置文件方法 ---
    private static void handleSelectLogic(Robot robot) {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastClickTime < CLICK_COOLDOWN) {
            return;
        }

        // --- 打印当前分辨率 ---
        Dimension size = Toolkit.getDefaultToolkit().getScreenSize();
        String currentResolution = (int)size.getWidth() + "x" + (int)size.getHeight();
        System.out.println(">>> [Check] 准备点击，当前屏幕分辨率为: " + currentResolution);

        int x = currentButtonConfig.x;
        int y = currentButtonConfig.y;

        System.out.println(">>> 执行点击动作: 坐标 (" + x + ", " + y + ")");
        robot.mouseMove(x, y);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.delay(100);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        lastClickTime = currentTime;
    }

    // --- 发送 POST 请求的方法 (异步) ---
    private static void sendPostRequest(String currentStatus) {
        JSONObject jsonObject = new JSONObject();
        JSONObject contentObj = new JSONObject();
        contentObj.put("content", "当前状态非游戏画面,请检查:"+currentStatus);
        jsonObject.put("msgtype", "text");
        jsonObject.put("text", contentObj);



        // 1. 构建 JSON 数据

        // 2. 创建 RequestBody
        RequestBody body = RequestBody.create(jsonObject.toJSONString(), MediaType.get("application/json; charset=utf-8"));

        // 3. 构建请求
        Request request = new Request.Builder()
                .url(fullApiUrl)
                .post(body)
                .build();

        // 4. 异步发送 (不会卡住主线程)
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                System.err.println("请求发送失败: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    System.out.println("通知已发送成功，服务器返回: " + response.code());
                } else {
                    System.err.println("发送失败，服务器返回错误码: " + response.code());
                }
                response.close(); // 记得关闭
            }
        });
    }
}