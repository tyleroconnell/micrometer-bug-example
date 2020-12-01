package io.micrometer.shaded.io.netty.util;

import io.micrometer.shaded.io.netty.util.concurrent.FastThreadLocal;
import io.micrometer.shaded.io.netty.util.internal.MathUtil;
import io.micrometer.shaded.io.netty.util.internal.ObjectPool;
import io.micrometer.shaded.io.netty.util.internal.SystemPropertyUtil;
import io.micrometer.shaded.io.netty.util.internal.logging.InternalLogger;
import io.micrometer.shaded.io.netty.util.internal.logging.InternalLoggerFactory;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class Recycler<T> {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(Recycler.class);

    private static final Handle NOOP_HANDLE = new Handle() {
        public void recycle(Object object) {
        }
    };

    private static final AtomicInteger ID_GENERATOR = new AtomicInteger(-2147483648);

    private static final int OWN_THREAD_ID = ID_GENERATOR.getAndIncrement();

    private static final int DEFAULT_INITIAL_MAX_CAPACITY_PER_THREAD = 4096;

    private static final int DEFAULT_MAX_CAPACITY_PER_THREAD;

    private static final int INITIAL_CAPACITY;

    static {
        sysout("Static Start");
        int maxCapacityPerThread = SystemPropertyUtil.getInt("io.micrometer.shaded.io.netty.recycler.maxCapacityPerThread",
                SystemPropertyUtil.getInt("io.micrometer.shaded.io.netty.recycler.maxCapacity", 4096));
        if (maxCapacityPerThread < 0)
            maxCapacityPerThread = 4096;
        DEFAULT_MAX_CAPACITY_PER_THREAD = maxCapacityPerThread;
        sysout("Static End");
    }

    private static void sysout(String format, Object ... args) {
        System.out.println(String.format("BUGLOG time: %d thread:%s tid: %d %s", System.currentTimeMillis(), Thread.currentThread().getName(),
                Thread.currentThread().getId(),
                String.format(format, args)));
        System.out.flush();
    }
    private void sysout2(String format, Object ... args) {
        System.out.println(String.format("BUGLOG2 time: %d thread:%s tid: %d classid: %s %s", System.currentTimeMillis(), Thread.currentThread().getName(),
                Thread.currentThread().getId(),
                this,
                String.format(format, args)));
        System.out.flush();
    }

    private static final int MAX_SHARED_CAPACITY_FACTOR = Math.max(2,
            SystemPropertyUtil.getInt("io.micrometer.shaded.io.netty.recycler.maxSharedCapacityFactor", 2));

    private static final int MAX_DELAYED_QUEUES_PER_THREAD = Math.max(0,
            SystemPropertyUtil.getInt("io.micrometer.shaded.io.netty.recycler.maxDelayedQueuesPerThread",

                    NettyRuntime.availableProcessors() * 2));

    private static final int LINK_CAPACITY = MathUtil.safeFindNextPositivePowerOfTwo(
            Math.max(SystemPropertyUtil.getInt("io.micrometer.shaded.io.netty.recycler.linkCapacity", 16), 16));

    private static final int RATIO = Math.max(0, SystemPropertyUtil.getInt("io.micrometer.shaded.io.netty.recycler.ratio", 8));

    private static final int DELAYED_QUEUE_RATIO = Math.max(0, SystemPropertyUtil.getInt("io.micrometer.shaded.io.netty.recycler.delayedQueue.ratio", RATIO));

    private final int maxCapacityPerThread;

    private final int maxSharedCapacityFactor;

    private final int interval;

    private final int maxDelayedQueuesPerThread;

    private final int delayedQueueInterval;

    static {
        sysout("Static2 Start");
        if (logger.isDebugEnabled()) {
            if (DEFAULT_MAX_CAPACITY_PER_THREAD == 0) {
                logger.debug("-Dio.netty.recycler.maxCapacityPerThread: disabled");
                logger.debug("-Dio.netty.recycler.maxSharedCapacityFactor: disabled");
                logger.debug("-Dio.netty.recycler.linkCapacity: disabled");
                logger.debug("-Dio.netty.recycler.ratio: disabled");
                logger.debug("-Dio.netty.recycler.delayedQueue.ratio: disabled");
            } else {
                sysout("Static2 first log message");
                long start = System.currentTimeMillis();
                while (System.currentTimeMillis()-start < 100) {
                    logger.debug("-Dio.netty.recycler.maxCapacityPerThread: {}", Integer.valueOf(DEFAULT_MAX_CAPACITY_PER_THREAD));
                    logger.debug("-Dio.netty.recycler.maxSharedCapacityFactor: {}", Integer.valueOf(MAX_SHARED_CAPACITY_FACTOR));
                    logger.debug("-Dio.netty.recycler.linkCapacity: {}", Integer.valueOf(LINK_CAPACITY));
                    logger.debug("-Dio.netty.recycler.ratio: {}", Integer.valueOf(RATIO));
                    logger.debug("-Dio.netty.recycler.delayedQueue.ratio: {}", Integer.valueOf(DELAYED_QUEUE_RATIO));
                    logger.debug("some crap");
                }
                sysout("Static2 last log message");
            }
        }
        sysout("Static2 Debug Block Ended: max_def: %d", DEFAULT_MAX_CAPACITY_PER_THREAD);
        INITIAL_CAPACITY = Math.min(DEFAULT_MAX_CAPACITY_PER_THREAD, 256);
        sysout("Static2 INITIAL_CAPACITY: %d", INITIAL_CAPACITY);

        DELAYED_RECYCLED = new FastThreadLocal<Map<Stack<?>, WeakOrderQueue>>() {
            protected Map<Recycler.Stack<?>, Recycler.WeakOrderQueue> initialValue() {
                sysout("new FastThreadLocal returning new WeakHashMap");
                return new WeakHashMap<Recycler.Stack<?>, Recycler.WeakOrderQueue>();
            }
        };
        sysout("Static2 Block Ended");
    }

    private final FastThreadLocal<Stack<T>> threadLocal = new FastThreadLocal<Stack<T>>() {
        protected Recycler.Stack<T> initialValue() {
            sysout2("creating stack recycler: %s thread: %s maxCapacityPerThread: %s maxSharedCapacityFactor:%s interval: %s maxDelayedQueuesPerThread: %s delayedQueueInterval: %s",
                    Recycler.this, Thread.currentThread(), Recycler.this.maxCapacityPerThread,
                    Recycler.this.maxSharedCapacityFactor,
                    Recycler.this.interval,
                    Recycler.this.maxDelayedQueuesPerThread,
                    Recycler.this.delayedQueueInterval);
            return new Recycler.Stack<T>(Recycler.this, Thread.currentThread(), Recycler.this.maxCapacityPerThread, Recycler.this.maxSharedCapacityFactor, Recycler.this.interval, Recycler.this.maxDelayedQueuesPerThread, Recycler.this.delayedQueueInterval);
        }

        protected void onRemoval(Recycler.Stack<T> value) {
            sysout2("onRemoval: stack: %s", value);
            if (value.threadRef.get() == Thread.currentThread() && Recycler.DELAYED_RECYCLED.isSet()) {
                ((Map) Recycler.DELAYED_RECYCLED.get()).remove(value);
            }
        }
    };

    private static final FastThreadLocal<Map<Stack<?>, WeakOrderQueue>> DELAYED_RECYCLED;

    protected Recycler() {
        this(DEFAULT_MAX_CAPACITY_PER_THREAD);
    }

    protected Recycler(int maxCapacityPerThread) {
        this(maxCapacityPerThread, MAX_SHARED_CAPACITY_FACTOR);
        sysout2("constructor(maxCapacityPerThread): maxCapacityPerThread: %d",
                maxCapacityPerThread);
    }

    protected Recycler(int maxCapacityPerThread, int maxSharedCapacityFactor) {
        this(maxCapacityPerThread, maxSharedCapacityFactor, RATIO, MAX_DELAYED_QUEUES_PER_THREAD);
        sysout2("constructor(maxCapacityPerThread, maxSharedCapacityFactor): maxCapacityPerThread: %d maxSharedCapacityFactor: %d",
                maxCapacityPerThread, maxSharedCapacityFactor);
    }

    protected Recycler(int maxCapacityPerThread, int maxSharedCapacityFactor, int ratio, int maxDelayedQueuesPerThread) {
        this(maxCapacityPerThread, maxSharedCapacityFactor, ratio, maxDelayedQueuesPerThread, DELAYED_QUEUE_RATIO);
        sysout2("constructor(maxCapacityPerThread, maxSharedCapacityFactor, ratio, maxDelayedQueuesPerThread): maxCapacityPerThread: %d maxSharedCapacityFactor: %d ratio: %d maxDelayedQueuesPerThread: %d",
                maxCapacityPerThread,
                maxSharedCapacityFactor,
                ratio,
                maxDelayedQueuesPerThread);
    }

    protected Recycler(int maxCapacityPerThread, int maxSharedCapacityFactor, int ratio, int maxDelayedQueuesPerThread, int delayedQueueRatio) {
        sysout2("constructor(maxCapacityPerThread, maxSharedCapacityFactor, ratio, maxDelayedQueuesPerThread, delayedQueueRatio): maxCapacityPerThread: %s maxSharedCapacityFactor: %s ratio: %s maxDelayedQueuesPerThread: %s delayedQueueRatio:%s",
                maxCapacityPerThread,
                maxSharedCapacityFactor,
                ratio,
                maxDelayedQueuesPerThread,
                delayedQueueRatio);

        this.interval = Math.max(0, ratio);
        this.delayedQueueInterval = Math.max(0, delayedQueueRatio);
        if (maxCapacityPerThread <= 0) {
            this.maxCapacityPerThread = 0;
            this.maxSharedCapacityFactor = 1;
            this.maxDelayedQueuesPerThread = 0;
        } else {
            this.maxCapacityPerThread = maxCapacityPerThread;
            this.maxSharedCapacityFactor = Math.max(1, maxSharedCapacityFactor);
            this.maxDelayedQueuesPerThread = Math.max(0, maxDelayedQueuesPerThread);
        }
        sysout2("constructor param: interval: %d", interval);
        sysout2("constructor param: delayedQueueInterval: %d", delayedQueueInterval);
        sysout2("constructor param: maxCapacityPerThread: %d", maxCapacityPerThread);
        sysout2("constructor param: maxSharedCapacityFactor: %d", maxSharedCapacityFactor);
        sysout2("constructor param: maxDelayedQueuesPerThread: %d", maxDelayedQueuesPerThread);
    }

    public final T get() {
        if (this.maxCapacityPerThread == 0)
            return newObject((Handle<T>) NOOP_HANDLE);
        Stack<T> stack = (Stack<T>) this.threadLocal.get();
        DefaultHandle<T> handle = stack.pop();
        if (handle == null) {
            handle = stack.newHandle();
            handle.value = newObject(handle);
        }
        return (T) handle.value;
    }

    @Deprecated
    public final boolean recycle(T o, Handle<T> handle) {
        if (handle == NOOP_HANDLE)
            return false;
        DefaultHandle<T> h = (DefaultHandle<T>) handle;
        if (h.stack.parent != this)
            return false;
        h.recycle(o);
        return true;
    }

    final int threadLocalCapacity() {
        return ((Stack) this.threadLocal.get()).elements.length;
    }

    final int threadLocalSize() {
        return ((Stack) this.threadLocal.get()).size;
    }

    protected abstract T newObject(Handle<T> paramHandle);

    private static final class DefaultHandle<T> implements Handle<T> {
        int lastRecycledId;

        int recycleId;

        boolean hasBeenRecycled;

        Recycler.Stack<?> stack;

        Object value;

        DefaultHandle(Recycler.Stack<?> stack) {
            this.stack = stack;
        }

        public void recycle(Object object) {
            if (object != this.value)
                throw new IllegalArgumentException("object does not belong to handle");
            Recycler.Stack<?> stack = this.stack;
            if (this.lastRecycledId != this.recycleId || stack == null)
                throw new IllegalStateException("recycled already");
            stack.push(this);
        }
    }

    private static final class WeakOrderQueue extends WeakReference<Thread> {
        static final WeakOrderQueue DUMMY = new WeakOrderQueue();

        private final Head head;

        private Link tail;

        private WeakOrderQueue next;

        static final class Link extends AtomicInteger {
            final Recycler.DefaultHandle<?>[] elements = (Recycler.DefaultHandle<?>[]) new Recycler.DefaultHandle[Recycler.LINK_CAPACITY];

            int readIndex;

            Link next;
        }

        private static final class Head {
            private final AtomicInteger availableSharedCapacity;

            Recycler.WeakOrderQueue.Link link;

            Head(AtomicInteger availableSharedCapacity) {
                this.availableSharedCapacity = availableSharedCapacity;
            }

            void reclaimAllSpaceAndUnlink() {
                Recycler.WeakOrderQueue.Link head = this.link;
                this.link = null;
                int reclaimSpace = 0;
                while (head != null) {
                    reclaimSpace += Recycler.LINK_CAPACITY;
                    Recycler.WeakOrderQueue.Link next = head.next;
                    head.next = null;
                    head = next;
                }
                if (reclaimSpace > 0)
                    reclaimSpace(reclaimSpace);
            }

            private void reclaimSpace(int space) {
                this.availableSharedCapacity.addAndGet(space);
            }

            void relink(Recycler.WeakOrderQueue.Link link) {
                reclaimSpace(Recycler.LINK_CAPACITY);
                this.link = link;
            }

            Recycler.WeakOrderQueue.Link newLink() {
                return reserveSpaceForLink(this.availableSharedCapacity) ? new Recycler.WeakOrderQueue.Link() : null;
            }

            static boolean reserveSpaceForLink(AtomicInteger availableSharedCapacity) {
                while (true) {
                    int available = availableSharedCapacity.get();
                    if (available < Recycler.LINK_CAPACITY)
                        return false;
                    if (availableSharedCapacity.compareAndSet(available, available - Recycler.LINK_CAPACITY))
                        return true;
                }
            }
        }

        private final int id = Recycler.ID_GENERATOR.getAndIncrement();

        private final int interval;

        private int handleRecycleCount;

        private WeakOrderQueue() {
            super((Thread) null);
            this.head = new Head(null);
            this.interval = 0;
        }

        private WeakOrderQueue(Recycler.Stack<?> stack, Thread thread) {
            super(thread);
            this.tail = new Link();
            this.head = new Head(stack.availableSharedCapacity);
            this.head.link = this.tail;
            this.interval = stack.delayedQueueInterval;
            this.handleRecycleCount = this.interval;
            sysout("WeakOrderQueue: elements: %s",((stack.elements!=null)?String.valueOf(stack.elements.length):"null"));
        }

        static WeakOrderQueue newQueue(Recycler.Stack<?> stack, Thread thread) {
            if (!Head.reserveSpaceForLink(stack.availableSharedCapacity))
                return null;
            WeakOrderQueue queue = new WeakOrderQueue(stack, thread);
            stack.setHead(queue);
            return queue;
        }

        WeakOrderQueue getNext() {
            return this.next;
        }

        void setNext(WeakOrderQueue next) {
            assert next != this;
            this.next = next;
        }

        void reclaimAllSpaceAndUnlink() {
            this.head.reclaimAllSpaceAndUnlink();
            this.next = null;
        }

        void add(Recycler.DefaultHandle<?> handle) {
            handle.lastRecycledId = this.id;
            if (this.handleRecycleCount < this.interval) {
                this.handleRecycleCount++;
                return;
            }
            this.handleRecycleCount = 0;
            Link tail = this.tail;
            int writeIndex;
            if ((writeIndex = tail.get()) == Recycler.LINK_CAPACITY) {
                Link link = this.head.newLink();
                if (link == null)
                    return;
                this.tail = tail = tail.next = link;
                writeIndex = tail.get();
            }
            tail.elements[writeIndex] = handle;
            handle.stack = null;
            tail.lazySet(writeIndex + 1);
        }

        boolean hasFinalData() {
            return (this.tail.readIndex != this.tail.get());
        }

        boolean transfer(Recycler.Stack<?> dst) {
            sysout("transfer dst: %s", dst);

            Link head = this.head.link;
            if (head == null)
                return false;
            if (head.readIndex == Recycler.LINK_CAPACITY) {
                if (head.next == null)
                    return false;
                head = head.next;
                this.head.relink(head);
            }
            int srcStart = head.readIndex;
            int srcEnd = head.get();
            int srcSize = srcEnd - srcStart;
            if (srcSize == 0)
                return false;
            int dstSize = dst.size;
            int expectedCapacity = dstSize + srcSize;
            sysout("transfer exp: %d len: %d", expectedCapacity, dst.elements.length);

            if (expectedCapacity > dst.elements.length) {
                sysout("calling increaseCapacity exp: %d len: %d", expectedCapacity, dst.elements.length);
                int actualCapacity = dst.increaseCapacity(expectedCapacity);
                srcEnd = Math.min(srcStart + actualCapacity - dstSize, srcEnd);
            }
            if (srcStart != srcEnd) {
                Recycler.DefaultHandle<?>[] arrayOfDefaultHandle1 = head.elements;
                Recycler.DefaultHandle<?>[] arrayOfDefaultHandle2 = dst.elements;
                int newDstSize = dstSize;
                for (int i = srcStart; i < srcEnd; i++) {
                    Recycler.DefaultHandle<?> element = arrayOfDefaultHandle1[i];
                    if (element.recycleId == 0) {
                        element.recycleId = element.lastRecycledId;
                    } else if (element.recycleId != element.lastRecycledId) {
                        throw new IllegalStateException("recycled already");
                    }
                    arrayOfDefaultHandle1[i] = null;
                    if (!dst.dropHandle(element)) {
                        element.stack = dst;
                        arrayOfDefaultHandle2[newDstSize++] = element;
                    }
                }
                if (srcEnd == Recycler.LINK_CAPACITY && head.next != null)
                    this.head.relink(head.next);
                head.readIndex = srcEnd;
                if (dst.size == newDstSize)
                    return false;
                dst.size = newDstSize;
                return true;
            }
            return false;
        }
    }

    private static final class Stack<T> {
        final Recycler<T> parent;

        final WeakReference<Thread> threadRef;

        final AtomicInteger availableSharedCapacity;

        private final int maxDelayedQueues;

        private final int maxCapacity;

        private final int interval;

        private final int delayedQueueInterval;

        Recycler.DefaultHandle<?>[] elements;

        int size;

        private int handleRecycleCount;

        private Recycler.WeakOrderQueue cursor;

        private Recycler.WeakOrderQueue prev;

        private volatile Recycler.WeakOrderQueue head;

        Stack(Recycler<T> parent, Thread thread, int maxCapacity, int maxSharedCapacityFactor, int interval, int maxDelayedQueues, int delayedQueueInterval) {
            this.parent = parent;
            this.threadRef = new WeakReference<Thread>(thread);
            this.maxCapacity = maxCapacity;
            this.availableSharedCapacity = new AtomicInteger(Math.max(maxCapacity / maxSharedCapacityFactor, Recycler.LINK_CAPACITY));
            sysout("stack constructor init: %d max: %d", Recycler.INITIAL_CAPACITY, maxCapacity);
            this.elements = (Recycler.DefaultHandle<?>[]) new Recycler.DefaultHandle[Math.min(Recycler.INITIAL_CAPACITY, maxCapacity)];
            sysout("stack constructor elements: %d", this.elements.length);
            this.interval = interval;
            this.delayedQueueInterval = delayedQueueInterval;
            this.handleRecycleCount = interval;
            this.maxDelayedQueues = maxDelayedQueues;
        }

        synchronized void setHead(Recycler.WeakOrderQueue queue) {
            queue.setNext(this.head);
            this.head = queue;
        }

        int increaseCapacity(int expectedCapacity) {
            int newCapacity = this.elements.length;
            int maxCapacity = this.maxCapacity;
            sysout("increaseCapacity init: %d max: %d len: %d", newCapacity, maxCapacity, this.elements.length);
            do {
                sysout("loop: %d max: %d len: %d", newCapacity , maxCapacity, this.elements.length);
                newCapacity <<= 1;
            } while (newCapacity < expectedCapacity && newCapacity < maxCapacity);
            newCapacity = Math.min(newCapacity, maxCapacity);
            if (newCapacity != this.elements.length) {
                this.elements = (Recycler.DefaultHandle<?>[]) Arrays.<Recycler.DefaultHandle>copyOf((Recycler.DefaultHandle[]) this.elements, newCapacity);
                sysout("increaseCapacity elements: %d", this.elements.length);
            }
            return newCapacity;
        }

        Recycler.DefaultHandle<T> pop() {
            sysout("pop");
            int size = this.size;
            if (size == 0) {
                sysout("pop scavenge");
                if (!scavenge())
                    return null;
                size = this.size;
                if (size <= 0)
                    return null;
            }
            size--;
            Recycler.DefaultHandle<?> ret = this.elements[size];
            this.elements[size] = null;
            this.size = size;
            if (ret.lastRecycledId != ret.recycleId)
                throw new IllegalStateException("recycled multiple times");
            ret.recycleId = 0;
            ret.lastRecycledId = 0;
            return (Recycler.DefaultHandle) ret;
        }

        private boolean scavenge() {
            if (scavengeSome())
                return true;
            this.prev = null;
            this.cursor = this.head;
            return false;
        }

        private boolean scavengeSome() {
            Recycler.WeakOrderQueue prev, cursor = this.cursor;
            if (cursor == null) {
                prev = null;
                cursor = this.head;
                if (cursor == null)
                    return false;
            } else {
                prev = this.prev;
            }
            boolean success = false;
            do {
                if (cursor.transfer(this)) {
                    success = true;
                    break;
                }
                Recycler.WeakOrderQueue next = cursor.getNext();
                if (cursor.get() == null) {
                    if (cursor.hasFinalData())
                        while (cursor.transfer(this))
                            success = true;
                    if (prev != null) {
                        cursor.reclaimAllSpaceAndUnlink();
                        prev.setNext(next);
                    }
                } else {
                    prev = cursor;
                }
                cursor = next;
            } while (cursor != null && !success);
            this.prev = prev;
            this.cursor = cursor;
            return success;
        }

        void push(Recycler.DefaultHandle<?> item) {
            Thread currentThread = Thread.currentThread();
            if (this.threadRef.get() == currentThread) {
                pushNow(item);
            } else {
                pushLater(item, currentThread);
            }
        }

        private void pushNow(Recycler.DefaultHandle<?> item) {
            if ((item.recycleId | item.lastRecycledId) != 0)
                throw new IllegalStateException("recycled already");
            item.recycleId = item.lastRecycledId = Recycler.OWN_THREAD_ID;
            int size = this.size;
            if (size >= this.maxCapacity || dropHandle(item))
                return;
            if (size == this.elements.length)
                this.elements = (Recycler.DefaultHandle<?>[]) Arrays.<Recycler.DefaultHandle>copyOf((Recycler.DefaultHandle[]) this.elements, Math.min(size << 1, this.maxCapacity));
            this.elements[size] = item;
            this.size = size + 1;
        }

        private void pushLater(Recycler.DefaultHandle<?> item, Thread thread) {
            if (this.maxDelayedQueues == 0)
                return;
            Map<Stack<?>, Recycler.WeakOrderQueue> delayedRecycled = (Map<Stack<?>, Recycler.WeakOrderQueue>) Recycler.DELAYED_RECYCLED.get();
            Recycler.WeakOrderQueue queue = delayedRecycled.get(this);
            if (queue == null) {
                if (delayedRecycled.size() >= this.maxDelayedQueues) {
                    delayedRecycled.put(this, Recycler.WeakOrderQueue.DUMMY);
                    return;
                }
                if ((queue = newWeakOrderQueue(thread)) == null)
                    return;
                delayedRecycled.put(this, queue);
            } else if (queue == Recycler.WeakOrderQueue.DUMMY) {
                return;
            }
            queue.add(item);
        }

        private Recycler.WeakOrderQueue newWeakOrderQueue(Thread thread) {
            return Recycler.WeakOrderQueue.newQueue(this, thread);
        }

        boolean dropHandle(Recycler.DefaultHandle<?> handle) {
            if (!handle.hasBeenRecycled) {
                if (this.handleRecycleCount < this.interval) {
                    this.handleRecycleCount++;
                    return true;
                }
                this.handleRecycleCount = 0;
                handle.hasBeenRecycled = true;
            }
            return false;
        }

        Recycler.DefaultHandle<T> newHandle() {
            return new Recycler.DefaultHandle<T>(this);
        }
    }

    public static interface Handle<T> extends ObjectPool.Handle<T> {
    }
}