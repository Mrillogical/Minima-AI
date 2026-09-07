package com.minima-AI.ai.websearch;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class WebSearchTool {

    private final ResilientWebSearchOps resilientWebSearchOps;

    @Tool(name = "web_search",
            description = """
                    Search the public web using Tavily.

                    Use this tool ONLY when:

                    - the URL belongs to an unfamiliar domain
                    - reputation information is required
                    - page content is insufficient
                    - the website may be malicious
                    - recent information is needed

                    Return only factual information.
                    """)
    public List<String> search(String query) {

        return resilientWebSearchOps
                .search(query)
                .join();
    }
}

