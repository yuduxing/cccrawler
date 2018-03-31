package com.hdcsd.crawler.cccrawler.exchange;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.springframework.stereotype.Component;

import java.util.Iterator;

@Component
public class BitstampExchangeProcess extends ExchangeProcessBase {
    @Override
    public String processTrade(String exchange, String symbol, String content) {
        JSONArray jsonArray = JSONArray.parseArray(content);
        JSONArray jsonArrayResult = new JSONArray();
        for (Iterator iterator = jsonArray.iterator(); iterator.hasNext();) {
            JSONObject jsonObject = (JSONObject) iterator.next();
            Long id = jsonObject.getLong("tid");
            String key = exchange + symbol + id;
            if(!inCache(key)){
                jsonArrayResult.add(jsonObject);
            }
        }

        return jsonArrayResult.toJSONString();
    }

    @Override
    public String processKline(String exchange, String symbol, String content) {
        return content;
    }
}