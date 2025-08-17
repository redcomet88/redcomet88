import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class OpenAIClient {
    private static final String BASE_URL = "https://api.siliconflow.cn";
    private final String apiKey;

    public OpenAIClient(String apiKey) {
        this.apiKey = apiKey;
    }

    public static void main(String[] args) {
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new RuntimeException("未找到环境变量 OPENAI_API_KEY");
        }

        OpenAIClient client = new OpenAIClient(apiKey);
        
        try {
            // 示例1：使用Qwen模型
            Response result = client.llmChat("你好，请做自我介绍", "Qwen/Qwen3-32B");
            System.out.println("Qwen回复:\n" + result.content);
            System.out.println("Reasoning: " + result.reasoningContent);
            
            // 示例2：使用DeepSeek推理模型
            Response reasoningResult = client.llmChat("1+1等于几？", "deepseek-reasoner");
            System.out.println("DeepSeek回复:\n" + reasoningResult.content);
            System.out.println("Reasoning: " + reasoningResult.reasoningContent);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Response llmChat(String question, String model) throws IOException {
        // 构造请求JSON
        String jsonInput = buildRequestJson(question, model);
        
        // 创建HTTP连接
        URL url = new URL(BASE_URL + "/v1/chat/completions");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Authorization", "Bearer " + apiKey);
        connection.setDoOutput(true);
        
        // 发送请求
        try (OutputStream os = connection.getOutputStream()) {
            byte[] input = jsonInput.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }
        
        // 获取响应
        int responseCode = connection.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new IOException("HTTP请求失败: " + responseCode + " - " + connection.getResponseMessage());
        }
        
        try (InputStream is = connection.getInputStream();
             BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
            StringBuilder jsonResponse = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                jsonResponse.append(line);
            }
            
            // 解析响应
            return parseResponse(jsonResponse.toString(), model);
        }
    }

    private String buildRequestJson(String question, String model) {
        // 转义question中的特殊字符
        String escapedQuestion = question.replace("\\", "\\\\")
                                        .replace("\"", "\\\"")
                                        .replace("\n", "\\n")
                                        .replace("\r", "\\r")
                                        .replace("\t", "\\t");
        
        return "{" +
                "\"model\": \"" + model + "\"," +
                "\"messages\": [{\"role\": \"user\", \"content\": \"" + escapedQuestion + "\"}]," +
                "\"temperature\": 0.7," +
                "\"stream\": false" +
                "}";
    }

    private Response parseResponse(String jsonResponse, String model) {
        // 查找内容字段
        String content = extractValue(jsonResponse, "\"content\":");
        String reasoningContent = "";
        
        // 如果是deepseek-reasoner模型，提取推理内容
        if ("deepseek-reasoner".equals(model)) {
            reasoningContent = extractValue(jsonResponse, "\"reasoning_content\":");
        }
        
        return new Response(content, reasoningContent);
    }

    private String extractValue(String json, String key) {
        int keyIndex = json.indexOf(key);
        if (keyIndex == -1) return "";
        
        int startIndex = json.indexOf('"', keyIndex + key.length()) + 1;
        if (startIndex == 0) return "";
        
        StringBuilder sb = new StringBuilder();
        boolean escapeMode = false;
        
        for (int i = startIndex; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escapeMode) {
                sb.append(c);
                escapeMode = false;
            } else if (c == '\\') {
                escapeMode = true;
            } else if (c == '"') {
                break;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    static class Response {
        String content;
        String reasoningContent;
        
        public Response(String content, String reasoningContent) {
            this.content = content;
            this.reasoningContent = reasoningContent;
        }
    }
}
