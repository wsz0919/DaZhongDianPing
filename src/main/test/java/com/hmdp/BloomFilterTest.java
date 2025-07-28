package com.hmdp;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.thread.ThreadUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisIdWorker;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import javax.annotation.Resource;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Description
 *
 * @Author wangshaozhe
 * @Date 2025/7/23 13:41
 * @Company:
 */
@SpringBootTest
@AutoConfigureMockMvc

@Slf4j
public class BloomFilterTest {

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private MockMvc mockMvc;

    @Resource
    private IUserService userService;

    @Resource
    private ObjectMapper mapper;

    private ExecutorService es = Executors.newFixedThreadPool(500);

    @Test
    public void testBloomFilter() {
        RBloomFilter<String> bloomFilter = redissonClient.getBloomFilter("shopFilter");
        // 遍历 15~1000，查看是否有被误判为存在的 id
        for (int i = 14; i <= 1000; i++) {
            String id = String.valueOf(i);
            if (bloomFilter.contains(id)) {
                System.out.println("被误判为存在的 id：" + id);
            }
        }
    }

    @Test
    void testIdWorker() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(300);
        //CountDownLatch 相当于 一个线程计数器，没执行完一个线程就调用countDown（）方法 -1 ，直到减为0，之后还会变成初始值。
        Runnable task = ()->{
            for (int i = 0; i < 100; i++) {
                long id = redisIdWorker.nextId("order");
                System.out.println("id = " + id);
            }
            latch.countDown();
        };
        long begin = System.currentTimeMillis();
        for (int i = 0; i < 300; i++) {
            es.submit(task);
        }
        //  等待所有任务执行完毕
        latch.await();
        long end = System.currentTimeMillis();
        System.out.println("耗时：" + (end - begin));
    }

    @Test
    // 忽视异常
    @SneakyThrows
    public void login() {
        // 查询数据库得到1000个号码
        List<String> phoneList = userService.lambdaQuery()
                .select(User::getPhone)
                .last("limit 1000")
                .list().stream().map(User::getPhone).collect(Collectors.toList());
        // 使用线程池，线程池总放入1000个号码，提高效率
        ExecutorService executorService = ThreadUtil.newExecutor(phoneList.size());
        // 创建List集合，存储生成的token。多线程下使用CopyOnWriteArrayList，实现读写分离，保障线程安全（ArrayList不能保障线程安全）
        List<String> tokenList = new CopyOnWriteArrayList<>();
        // 创建CountDownLatch（线程计数器）对象，用于协调线程间的同步
        CountDownLatch countDownLatch = new CountDownLatch(phoneList.size());
        // 遍历phoneList，发送请求，然后将获取的token写入tokenList中
        phoneList.forEach(phone -> {
            executorService.execute(() -> {
                try {
                    // 发送获取验证码的请求，获取验证码
                    String codeJson = mockMvc.perform(MockMvcRequestBuilders
                                    .post("/user/code")
                                    .queryParam("phone", phone))
                            .andExpect(MockMvcResultMatchers.status().isOk())
                            .andReturn().getResponse().getContentAsString();
                    // 将返回的JSON字符串反序列化为Result对象
                    Result result = mapper.readerFor(Result.class).readValue(codeJson);
                    Assert.isTrue(result.getSuccess(), String.format("获取“%s”手机号的验证码失败", phone));
                    String code = result.getData().toString();

                    // 创建一个登录表单
                    // 使用建造者模式构建 登录信息对象，我这里就没有使用了，我是直接使用new（效率较低不推荐使用）
//                    LoginFormDTO formDTO = LoginFormDTO.builder().code(code).phone(phone).build();
                    LoginFormDTO formDTO = new LoginFormDTO();
                    formDTO.setCode(code);
                    formDTO.setPhone(phone);
                    // 将LoginFormDTO对象序列化为JSON
                    String json = mapper.writeValueAsString(formDTO);

                    // 发送登录请求，获取token
                    // 发送登录请求，获取返回信息（JSON字符串，其中包含token）
                    String tokenJson = mockMvc.perform(MockMvcRequestBuilders
                                    .post("/user/login").content(json).contentType(MediaType.APPLICATION_JSON))
                            .andExpect(MockMvcResultMatchers.status().isOk())
                            .andReturn().getResponse().getContentAsString();
                    // 将JSON字符串反序列化为Result对象
                    result = mapper.readerFor(Result.class).readValue(tokenJson);
                    Assert.isTrue(result.getSuccess(), String.format("获取“%s”手机号的token失败,json为“%s”", phone, json));
                    String token = result.getData().toString();
                    tokenList.add(token);
                    // 线程计数器减一
                    countDownLatch.countDown();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        });
        // 线程计数器为0时，表示所有线程执行完毕，此时唤醒主线程
        countDownLatch.await();
        // 关闭线程池
        executorService.shutdown();
        Assert.isTrue(tokenList.size() == phoneList.size());
        // 所有线程都获取了token，此时将所有的token写入tokens.txt文件中
        writeToTxt(tokenList, "\\tokens.txt");
        log.info("程序执行完毕！");
    }

    /**
     * 生成tokens.txt文件
     * @param list
     * @param suffixPath
     * @throws Exception
     */
    private static void writeToTxt(List<String> list, String suffixPath) throws Exception {
        // 1. 创建文件
        File file = new File(System.getProperty("user.dir") + "\\src\\main\\resources" + suffixPath);
        if (!file.exists()) {
            file.createNewFile();
        }
        // 2. 输出
        BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8));
        for (String content : list) {
            bw.write(content);
            bw.newLine();
        }
        bw.close();
        log.info("tokens.txt文件生成完毕！");
    }

}
