package com.xlongwei.search;

import com.networknt.server.ShutdownHookProvider;

/**
 * 关闭所有已打开索引
 */
public class SearchShutdown implements ShutdownHookProvider {

    @Override
    public void onShutdown() {
        SearchHandler.shutdown();
    }

}
