package org.mymmsc.aio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;

/**
 * 异步网络接口
 *
 * @author wangfeng
 * @date 2016年1月9日 下午5:10:39
 */
public abstract class Asio<T extends AioContext> extends AioBenchmark
        implements AioHandler, Closeable, CompletionHandler<T> {
    protected Logger logger = LoggerFactory.getLogger(getClass());
    public final static int AE_ACCEPT = SelectionKey.OP_ACCEPT;
    public final static int AE_CONNECT = SelectionKey.OP_CONNECT;
    public final static int AE_READ = SelectionKey.OP_READ;
    public final static int AE_WRITE = SelectionKey.OP_WRITE;
    private AioHandler handler = null;

    /**
     * 选择器
     */
    protected Selector selector = null;
    /**
     * 是否运行
     */
    protected boolean done = false;

    public Asio(int number, int concurrency) throws IOException{
        super(number, concurrency);
        selector = Selector.open();
    }

    public Selector getSelector() {
        return selector;
    }

    @Override
    public void close() {
        try {
            selector.close();
        } catch (IOException e) {
            logger.error("Selector close failed: ", e);
        }
    }

    /**
     * 更新计时
     *
     * @param context
     */
    private void updateTime(T context) {
        context.setStartTime(System.currentTimeMillis());
    }

    protected SelectionKey keyFor(SocketChannel sc) {
        SelectionKey key = sc.keyFor(selector);
        return key;
    }

    @SuppressWarnings("unchecked")
    protected T contextFor(SelectionKey key) {
        return (T) key.attachment();
    }

    protected T contextFor(SocketChannel sc) {
        SelectionKey sk = keyFor(sc);
        return contextFor(sk);
    }

    /**
     * 关闭网络通道
     *
     * @param sc
     */
    protected void closeChannel(SocketChannel sc) {
        try {
            SelectionKey key = keyFor(sc);
            if (key != null) {
                key.cancel();
            }
            sc.close();
        } catch (IOException e) {
            logger.error("SocketChannel close failed: ", e);
        } finally {
            //
        }
    }

    @Override
    public void handleCompact(Selector selector) {
        onCompact(null);
    }

    @Override
    public void handleAccepted(SocketChannel sc) {
        T context = contextFor(sc);
        onAccepted(context);
    }

    /**
     * channel关闭处理
     *
     * @param sc
     */
    @Override
    public void handleClosed(SocketChannel sc) {
        T context = contextFor(sc);
        onClosed(context);
        closeChannel(sc);
    }

    @Override
    public void handleError(SocketChannel sc) {
        T context = contextFor(sc);
        onError(context);
        handleClosed(sc);
    }

    /**
     * 超时处理, 随后调用关闭channel方法
     *
     * @param sc
     */
    @Override
    public void handleTimeout(SocketChannel sc) {
        onTimeout(contextFor(sc));
        handleClosed(sc);
    }

    @Override
    public void handleConnected(SocketChannel sc) {
        SelectionKey sk = keyFor(sc);
        T context = contextFor(sc);
        // 如果客户端没有立即连接到服务端, 则客户端完成非立即连接操作
        try {
            // 设置成非阻塞
            //sc.configureBlocking(false);
            //if (sc.finishConnect()) {
                // 处理完后必须吧OP_CONNECT关注去掉, 改为关注OP_READ
                onConnected(context);
                sk.interestOps(SelectionKey.OP_READ);
            //} else {
            //    handleError(sc);
            //}
        } /*catch (ConnectException e) {
            // java.net.ConnectException: Operation timed out
            handleTimeout(sc);
        } catch (IOException e) {
            handleError(sc);
        }*/ catch (Exception e) {
            handleError(sc);
        }
    }

    /**
     * channel读数据
     *
     * @param sc
     */
    @Override
    public void handleRead(SocketChannel sc) {
        SelectionKey sk = keyFor(sc);
        T context = contextFor(sc);
        int bufferLen = 128 * 1024;
        //bufferLen = 1;
        ByteBuffer buf = ByteBuffer.allocate(bufferLen);
        long bytesRead = 0;
        try {
            bytesRead = sc.read(buf);
            T ctx = contextFor(sc);
            if (bytesRead == -1) { // Did the other end close?
                //onRead(ctx);
                //onCompleted(context);
                handleError(sc);
            } else if (bytesRead == 0) {
                //sk.interestOps(SelectionKey.OP_READ);
            } else if (bytesRead > 0) {
                ctx.add((ByteBuffer) buf.flip());
                onRead(ctx);
                if(bytesRead >= bufferLen) {
                    //sk.interestOps(SelectionKey.OP_READ);
                } else {
                    //
                }
                if(ctx != null && ctx.completed()){
                    onCompleted(context);
                    handleClosed(sc);
                } else {
                    //sk.interestOps(SelectionKey.OP_READ);
                }
            }
        } catch (Exception e) {
            logger.error("read error: ", e);
            handleError(sc);
        }
    }

    @Override
    public void handleWrite(SocketChannel sc) {
        SelectionKey sk = keyFor(sc);
        T context = contextFor(sc);
        onWrite(context);
    }

    /**
     * 检测所有在册的通道超时情况, 并处理
     */
    private void checkTimeout() {
        boolean bAction = false;
        Iterator<SelectionKey> it = selector.keys().iterator();
        while (it.hasNext()) {
            SelectionKey sk = (SelectionKey) it.next();
            SocketChannel sc = (SocketChannel) sk.channel();
            T context = contextFor(sk);
            if (context.isTimeout()) {
                handleTimeout(sc);
            }
            bAction = true;
        }
        if (!bAction) {
            //Api.sleep(100);
        }
    }

    /**
     * 网络事件监控主流程
     *
     * @return
     */
    public int start() {
        int iRet = -1;

        int num = 0;
        done = true;
        // 主流程不允许抛出异常
        while (done) {
            //System.out.println("---");
            try {
                // 超时检测
                num = selector.select(/*timeout*/1);
                //num = selector.selectNow();
                if (num < 1) {
                    // 全部超时
                    checkTimeout();
                } else {
                    // 遍历每个有可用IO操作Channel对应的SelectionKey
                    Iterator<SelectionKey> it = selector.selectedKeys().iterator();
                    while (it.hasNext()) {
                        final SelectionKey sk = (SelectionKey) it.next();
                        int ops = sk.readyOps();
                        boolean isAcceptable = (ops & SelectionKey.OP_ACCEPT) == SelectionKey.OP_ACCEPT;
                        boolean isConnectable = (ops & SelectionKey.OP_CONNECT) == SelectionKey.OP_CONNECT;
                        boolean isReadable = (ops & SelectionKey.OP_READ) == SelectionKey.OP_READ;
                        boolean isWritable = (ops & SelectionKey.OP_WRITE) == SelectionKey.OP_WRITE;
                        T context = contextFor(sk);
                        // 从集合中删除此次事件
                        it.remove();
                        SocketChannel channel = (SocketChannel) sk.channel();
                        if (isAcceptable) {
                            // 新客户端连接到达
                            ServerSocketChannel ssc = (ServerSocketChannel) sk.channel();
                            channel = ssc.accept();
                            // 将客户端套接字通过连接模式调整为非阻塞模式
                            channel.configureBlocking(false);
                            // 将客户端套接字通道OP_READ事件注册到通道选择器上
                            channel.register(selector, SelectionKey.OP_READ, null);
                            handleAccepted(channel);
                        } /*else if (sk.isConnectable() && !channel.isConnected()) {
                            // 当前通道选择器产生连接已经准备就绪事件, 并且客户端套接字
                            // 通道尚未连接到服务端套接字通道
                            handleConnected(channel);
                        }*/else if (isConnectable) {
                            //如果正在连接，则完成连接
                            if(channel.isConnectionPending()){
                                try {
                                    channel.finishConnect();
                                } catch (Exception e) {
                                    //
                                }
                            }
                            handleConnected(channel);
                        }
                        else if (isReadable) {
                            // 有数据可读, 读取数据字节数小于1, 即客户端断开, 需要关闭socket通道
                            handleRead(channel);
                        } else if (isWritable) {
                            // socket通道可写
                            handleWrite(channel);
                        } else {
                            // 一般情况下不太可能到达这个位置
                            //System.out.print('P');
                            handleError(channel);
                        }
                        // 更新时间
                        if (context != null) {
                            updateTime(context);
                        }
                    }
                    //
                }
                handleCompact(selector);
            } catch (Exception e) {
                e.printStackTrace();
                logger.error("Selector.select failed: ", e);
            } finally {
                //
            }
        }
        return iRet;
    }
}
