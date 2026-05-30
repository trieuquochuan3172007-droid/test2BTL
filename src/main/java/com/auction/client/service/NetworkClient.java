package com.auction.client.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

/**
 * Singleton quản lý kết nối TCP từ client tới AuctionServer.
 *
 * <p>Cung cấp hai chế độ giao tiếp:
 * <ul>
 *   <li><b>Request-Response:</b> {@link #sendRequest(String)} — gửi lệnh, nhận phản hồi đồng bộ.</li>
 *   <li><b>Realtime broadcast:</b> {@link #startListening()} + {@link #setListener(MessageListener)}
 *       — nhận thông báo chủ động từ server (Observer Pattern).</li>
 * </ul>
 * </p>
 *
 * <p>Lưu ý: {@code sendRequest} và vòng lặp {@code startListening} dùng chung
 * luồng đọc {@code in}. Để tránh xung đột, {@code sendRequest} nên được gọi
 * trên thread riêng (background thread), không gọi từ JavaFX Application Thread.</p>
 */
public class NetworkClient {

    private static final String DEFAULT_HOST = "localhost";
    private static final int    DEFAULT_PORT = 9999;

    private static volatile NetworkClient instance;

    private Socket       socket;
    private PrintWriter  out;
    private BufferedReader in;
    private MessageListener listener;
    private java.util.function.Consumer<String> notificationListener;
    private Thread listenerThread;
    private final java.util.concurrent.BlockingQueue<String> responseQueue = new java.util.concurrent.LinkedBlockingQueue<>();

    private NetworkClient() {}

    // -------------------------------------------------------------------------
    // Singleton (Double-Checked Locking)
    // -------------------------------------------------------------------------
    public static NetworkClient getInstance() {
        if (instance == null) {
            synchronized (NetworkClient.class) {
                if (instance == null) instance = new NetworkClient();
            }
        }
        return instance;
    }

    // -------------------------------------------------------------------------
    // Kết nối / Ngắt kết nối
    // -------------------------------------------------------------------------

    /**
     * Kết nối tới server theo host và port tuỳ chỉnh.
     *
     * @return true nếu kết nối thành công (hoặc đã kết nối từ trước)
     */
    public synchronized boolean connect(String host, int port) {
        if (isConnected()) return true;
        try {
            socket = new Socket(host, port);
            out    = new PrintWriter(socket.getOutputStream(), true);
            in     = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            // Đọc bỏ lời chào ban đầu từ server
            String welcome = in.readLine();
            System.out.println("[CLIENT] ✓ Kết nối thành công: " + welcome);
            return true;
        } catch (IOException e) {
            System.err.println("[CLIENT] ✗ Lỗi kết nối " + host + ":" + port + " — " + e.getMessage());
            return false;
        }
    }

    /** Kết nối với địa chỉ mặc định (localhost:9999). */
    public boolean connect() {
        return connect(DEFAULT_HOST, DEFAULT_PORT);
    }

    /** Ngắt kết nối sạch sẽ, gửi lệnh QUIT trước khi đóng socket. */
    public synchronized void disconnect() {
        try {
            if (out != null)  out.println("QUIT");
            if (socket != null && !socket.isClosed()) socket.close();
            if (listenerThread != null) listenerThread.interrupt();
        } catch (IOException e) {
            System.err.println("[CLIENT] Lỗi khi ngắt kết nối: " + e.getMessage());
        } finally {
            socket = null;
            out    = null;
            in     = null;
            responseQueue.clear();
        }
    }

    /** Kiểm tra xem client có đang kết nối tới server không. */
    public boolean isConnected() {
        return socket != null && !socket.isClosed() && socket.isConnected();
    }

    // -------------------------------------------------------------------------
    // Request - Response (đồng bộ)
    // -------------------------------------------------------------------------

    /**
     * Gửi lệnh tới server và chờ nhận một dòng phản hồi.
     *
     * <p>Tự động thử kết nối lại nếu mất kết nối.</p>
     *
     * @param request Lệnh gửi đi (ví dụ: "PLACE_BID|A1|BIDDER1|200000")
     * @return Phản hồi từ server, hoặc chuỗi "LOI|..." nếu có lỗi mạng
     */
    public synchronized String sendRequest(String request) {
        if (!isConnected()) {
            System.out.println("[CLIENT] Mất kết nối — thử kết nối lại...");
            if (!connect()) return "LOI|Không thể kết nối tới server";
        }
        try {
            out.println(request);
            if (listenerThread != null && listenerThread.isAlive()) {
                String response = responseQueue.poll(5, java.util.concurrent.TimeUnit.SECONDS);
                return response != null ? response : "LOI|Timeout khi chờ phản hồi từ server";
            } else {
                return in.readLine();
            }
        } catch (Exception e) {
            System.err.println("[CLIENT] Lỗi gửi/nhận: " + e.getMessage());
            return "LOI|Lỗi mạng: " + e.getMessage();
        }
    }

    // -------------------------------------------------------------------------
    // Realtime Observer — lắng nghe broadcast từ server
    // -------------------------------------------------------------------------

    /**
     * Đăng ký listener để nhận thông báo broadcast từ server.
     * Áp dụng Observer Pattern.
     */
    public void setListener(MessageListener listener) {
        this.listener = listener;
    }
    
    public void setNotificationListener(java.util.function.Consumer<String> listener) {
    this.notificationListener = listener;
    }

    /**
     * Bắt đầu vòng lặp đọc broadcast trên daemon thread.
     *
     * <p>Mỗi dòng nhận được sẽ được chuyển tới {@link MessageListener#onMessageReceived(String)}.
     * Vòng lặp tự kết thúc khi socket đóng.</p>
     */
    public synchronized void startListening() {
        if (listenerThread != null && listenerThread.isAlive()) return;
        listenerThread = new Thread(() -> {
            try {
                String line;
                while ((line = in.readLine()) != null) {
                    final String msg = line;
                    if (msg.startsWith("CAP_NHAT|")) {
                        if (listener != null) listener.onMessageReceived(msg);
                    } else if (msg.startsWith("PUSH_NOTIFICATION|")) {
                        if (notificationListener != null)
                            notificationListener.accept(msg.substring("PUSH_NOTIFICATION|".length()));
                    } else {
                        responseQueue.offer(msg);
                    }
                }
            } catch (IOException e) {
                System.err.println("[CLIENT] Mất kết nối broadcast: " + e.getMessage());
            }
        }, "network-listener");
        listenerThread.setDaemon(true); // Tự kết thúc khi JVM tắt
        listenerThread.start();
    }

    // -------------------------------------------------------------------------
    // Listener interface (Observer Pattern)
    // -------------------------------------------------------------------------
    @FunctionalInterface
    public interface MessageListener {
        void onMessageReceived(String message);
    }
}
