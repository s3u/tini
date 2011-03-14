/*
 * Copyright (c) 2011 CONTRIBUTORS
 *
 * This file is licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */

package tests.parser;

import java.io.IOException;
import java.io.InputStream;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * A stream backed channel.
 */
public class MockAsyncSocketChannel extends AsynchronousSocketChannel {

    private InputStream source;

    public MockAsyncSocketChannel(final InputStream source) {
        super(null);
        this.source = source;
    }

    @Override
    public AsynchronousSocketChannel bind(final SocketAddress local) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> AsynchronousSocketChannel setOption(final SocketOption<T> name, final T value) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public AsynchronousSocketChannel shutdownInput() throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public AsynchronousSocketChannel shutdownOutput() throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public SocketAddress getRemoteAddress() throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public <A> void connect(final SocketAddress remote, final A attachment,
                            final CompletionHandler<Void, ? super A> handler) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Future<Void> connect(final SocketAddress remote) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <A> void read(final ByteBuffer dst, final long timeout, final TimeUnit unit,
                         final A attachment, final CompletionHandler<Integer, ? super A> handler) {
        int b = -1;
        int count = 0;
        try {
            while(dst.hasRemaining() && (b = source.read()) != -1) {
                dst.put((byte) b);
                count++;
            }
            if(count == 0) {
                handler.completed(-1 , null);
            }
            else {
                handler.completed(count, null);
            }
        }
        catch(IOException ioe) {
            handler.failed(ioe, null);
        }
    }

    @Override
    public Future<Integer> read(final ByteBuffer dst) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <A> void read(final ByteBuffer[] dsts, final int offset, final int length,
                         final long timeout, final TimeUnit unit, final A attachment,
                         final CompletionHandler<Long, ? super A> handler) {
        throw new UnsupportedOperationException();
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public <A> void write(final ByteBuffer src, final long timeout, final TimeUnit unit,
                          final A attachment, final CompletionHandler<Integer, ? super A> handler) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public Future<Integer> write(final ByteBuffer src) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <A> void write(final ByteBuffer[] srcs, final int offset, final int length,
                          final long timeout, final TimeUnit unit, final A attachment,
                          final CompletionHandler<Long, ? super A> handler) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void close() throws IOException {
        // no-op
    }

    @Override
    public SocketAddress getLocalAddress() throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> T getOption(final SocketOption<T> name) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public Set<SocketOption<?>> supportedOptions() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isOpen() {
        throw new UnsupportedOperationException();
    }
}
