package com.hdcsd.crawler.cccrawler.service;

import com.alibaba.fastjson.JSON;
import com.google.common.base.Stopwatch;
import com.hdcsd.crawler.cccrawler.common.ExchangeProperties;
import com.hdcsd.crawler.cccrawler.entity.CommonMessageEntity;
import com.hdcsd.crawler.cccrawler.entity.ExchangeEntity;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

@Component
public class Crawler {
    private Logger logger = LoggerFactory.getLogger(getClass());

    private final List<HttpGet> httpGetList = new ArrayList<>();
    private int total = 0;
    private int success = 0;
    private int failed = 0;
    private int cancelled = 0;

    @Autowired
    private ExchangeProperties properties;

    @Autowired
    private KafkaProducer kafkaProducer;

    @Autowired
    private MessageConvertor messageConvertor;

    @PostConstruct
    public void init(){
        List<ExchangeEntity> exchangeEntities = properties.getList();
        for(ExchangeEntity entity : exchangeEntities){
            if(StringUtils.isNotEmpty(entity.getTradeurl())) {
                logger.info("Add new trade url:" + entity.getTradeurl());
                httpGetList.add(new HttpGet(entity.getTradeurl()));
            }

            if(StringUtils.isNotEmpty(entity.getKlineurl())) {
                logger.info("Add new kline url:" + entity.getKlineurl());
                httpGetList.add(new HttpGet(entity.getKlineurl()));
            }
        }

        total = httpGetList.size();
    }

    @Scheduled(fixedRate = 1000)
    public void run() throws InterruptedException, IOException {
        logger.info("start crawler");
        logger.info(JSON.toJSONString(properties.getList()));
        Stopwatch sw = Stopwatch.createStarted();
        RequestConfig requestConfig = RequestConfig.custom()
                .setSocketTimeout(3000)
                .setConnectTimeout(3000).build();
        CloseableHttpAsyncClient httpclient = HttpAsyncClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .build();
        try {
            httpclient.start();
            final CountDownLatch latch = new CountDownLatch(httpGetList.size());
            success = 0;
            failed = 0;
            cancelled = 0;
            for (final HttpGet request : httpGetList) {
                httpclient.execute(request, new FutureCallback<HttpResponse>() {

                    @Override
                    public void completed(final HttpResponse response) {
                        latch.countDown();
                        success++;
                        try {
                            String uri = request.getURI().toASCIIString();
                            String content = new String(IOUtils.toByteArray(response.getEntity().getContent()));
                            CommonMessageEntity entity = messageConvertor.convert(uri, content);
                            kafkaProducer.send(entity);
                        } catch (IOException e) {
                            logger.error(e.getMessage(), e);
                        }
                    }

                    @Override
                    public void failed(final Exception ex) {
                        latch.countDown();
                        failed++;
                        logger.info(request.getRequestLine() + "->" + ex);
                    }

                    @Override
                    public void cancelled() {
                        latch.countDown();
                        cancelled++;
                        logger.info(request.getRequestLine() + " cancelled");
                    }

                });
            }
            latch.await();
            logger.info("Shutting down");
        }
        catch (Exception e){
            logger.error(e.getMessage(), e);
        }
        finally {
            httpclient.close();
        }
        logger.info("Done");
        sw.stop();
        long millis = sw.elapsed(MILLISECONDS);
        logger.info("time: " + millis);
        logger.info(String.format("Total %d success %d failed %d cancelled %d",
                total, success, failed, cancelled));
    }
}