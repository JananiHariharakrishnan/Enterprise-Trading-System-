package com.tradingsystem.poller;

import java.util.List;

public interface SymbolSource {

    List<String> findSymbols();
}