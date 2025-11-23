package bot;

import ai.djl.Model;
import ai.djl.basicdataset.cv.classification.ImageFolder;
import ai.djl.metric.Metrics;
import ai.djl.modality.cv.transform.Resize;
import ai.djl.modality.cv.transform.ToTensor;
import ai.djl.ndarray.types.Shape;
import ai.djl.nn.Activation;
import ai.djl.nn.Block;
import ai.djl.nn.Blocks;
import ai.djl.nn.SequentialBlock;
import ai.djl.nn.convolutional.Conv2d;
import ai.djl.nn.core.Linear;
import ai.djl.nn.pooling.Pool;
import ai.djl.training.DefaultTrainingConfig;
import ai.djl.training.EasyTrain;
import ai.djl.training.Trainer;
import ai.djl.training.evaluator.Accuracy;
// 【修改点1】引入具体的日志监听器，而不是用 defaults
import ai.djl.training.listener.LoggingTrainingListener;
import ai.djl.training.loss.Loss;
import ai.djl.training.util.ProgressBar;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class GameSceneTrainer {

    public static void main(String[] args) throws Exception {
        // --- 1. 配置参数 ---
        int imageWidth = 128;
        int imageHeight = 128;
        int batchSize = 32;
        int epochs = 20;
        Path modelDir = Paths.get("build/model");
        Files.createDirectories(modelDir);

        System.out.println(">>> 正在加载数据集...");

        // --- 2. 加载数据集 ---
        ImageFolder dataset = ImageFolder.builder()
                .setRepositoryPath(Paths.get("dataset"))
                .optMaxDepth(10)
                .addTransform(new Resize(imageWidth, imageHeight))
                .addTransform(new ToTensor())
                .setSampling(batchSize, true)
                .build();

        dataset.prepare(new ProgressBar());

        System.out.println("识别到的分类标签: " + dataset.getSynset());
        int outputSize = dataset.getSynset().size();

        // --- 3. 构建自定义神经网络 (Simple CNN) ---
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
                .add(Linear.builder().setUnits(outputSize).build());

        try (Model model = Model.newInstance("game-classifier")) {
            model.setBlock(block);

            // --- 4. 设置训练配置 ---
            DefaultTrainingConfig config = new DefaultTrainingConfig(Loss.softmaxCrossEntropyLoss())
                    .addEvaluator(new Accuracy())
                    // 【修改点2】手动添加日志监听器，解决 cannot resolve method defaults 的问题
                    .addTrainingListeners(new LoggingTrainingListener());

            // --- 5. 开始训练 ---
            try (Trainer trainer = model.newTrainer(config)) {
                trainer.initialize(new Shape(1, 3, imageHeight, imageWidth));
                trainer.setMetrics(new Metrics());

                System.out.println(">>> 开始训练 (共 " + epochs + " 轮)...");

                EasyTrain.fit(trainer, epochs, dataset, null);

                System.out.println(">>> 训练完成！");

                // --- 6. 保存模型 ---
                model.save(modelDir, "game-classifier");
                System.out.println("模型已保存至: " + modelDir.toAbsolutePath());
            }
        }
    }
}