package org.example;

import ai.djl.Model;
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
import com.google.gson.JsonObject;
import okhttp3.*; // 引入 OkHttp

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

public class GameBot {

    // --- 新增：HTTP 客户端配置 ---
    private static final OkHttpClient client = new OkHttpClient();
    // 你的接收端 API 地址 (请修改这里)
    private static final String BASE_API_URL = "https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=";
     // 记录上一次发送通知的时间戳
    private static long lastNotificationTime = 0;
    // 冷却时间：5分钟 (毫秒)
    private static final long COOLDOWN = 5 * 60 * 1000;

    public static void main(String[] args) throws Exception {
        // --- 第一步：启动时读取配置文件 ---
        loadApiUrlFromConfig();
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
    private static String fullApiUrl = null;
    private static final String CONFIG_FILE_NAME = "config.txt";
    // --- 新增：读取配置文件方法 ---
    private static void loadApiUrlFromConfig() {
        try {
            Path path = Paths.get(CONFIG_FILE_NAME);
            if (Files.exists(path)) {
                // 读取文件内容并去除首尾空格
                String param = Files.readString(path).trim();
                if (!param.isEmpty()) {
                    fullApiUrl = BASE_API_URL + param;
                    System.out.println(">>> 成功加载配置文件，API地址: " + fullApiUrl);
                } else {
                    System.err.println(">>> 警告: config.txt 是空的！无法发送通知。");
                }
            } else {
                System.err.println(">>> 警告: 未找到 " + CONFIG_FILE_NAME + "，无法发送通知。");
                System.err.println(">>> 请在程序目录下创建 config.txt 并填入参数。");
            }
        } catch (IOException e) {
            System.err.println(">>> 读取配置文件出错: " + e.getMessage());
        }
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