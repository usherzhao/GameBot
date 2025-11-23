package bot;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

public class ScreenCollector {
    public static void main(String[] args) throws Exception {
        // 1. 设置截图保存的临时目录 (会自动创建)
        String savePath = "temp_screenshots";
        File dir = new File(savePath);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        Robot robot = new Robot();
        Rectangle screenRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());

        System.out.println(">>> 开始截图程序 <<<");
        System.out.println("请切换到游戏画面，程序每隔 1 秒会保存一张截图到: " + dir.getAbsolutePath());
        System.out.println("采集完成后，请手动停止程序 (在 IDEA 里点红色方块停止按钮)");

        while (true) {
            // 2. 截屏
            BufferedImage capture = robot.createScreenCapture(screenRect);

            // 3. 生成文件名
            String timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss-SSS").format(new Date());
            File file = new File(dir, timestamp + ".png");

            // 4. 保存
            ImageIO.write(capture, "png", file);
            System.out.println("已保存: " + file.getName());

            // 5. 暂停 1 秒
            Thread.sleep(1000);
        }
    }
}