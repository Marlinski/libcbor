package io.marlinski.libcbor;

import java.lang.reflect.Array;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Map;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.subscribers.DisposableSubscriber;

public class CborEncoder {

    private class Subscriber extends DisposableSubscriber<ByteBuffer> {
        private ByteBuffer upstream_current;
        private ByteBuffer downstream_next;

        Subscriber(int buffer_size) {
            downstream_next = ByteBuffer.allocate(buffer_size);
        }

        @Override
        protected void onStart() {
            request(1);
        }

        @Override
        public void onNext(ByteBuffer buf) {
            upstream_current = buf;
        }

        @Override
        public void onError(Throwable t) {
        }

        @Override
        public void onComplete() {
            upstream_current = null;
        }

        public ByteBuffer fill() {
            if (upstream_current == null) {
                request(1);
            }
            downstream_next.clear();

            while (upstream_current != null) {
                while (downstream_next.hasRemaining() && upstream_current.hasRemaining()) {
                    downstream_next.put(upstream_current.get());
                }
                if (!downstream_next.hasRemaining()) {
                    downstream_next.flip();
                    return downstream_next;
                }
                request(1);
            }
            if (downstream_next.position() > 0) {
                downstream_next.flip();
                return downstream_next;
            } else {
                return null;
            }
        }
    }

    private Flowable<ByteBuffer> flow;

    public CborEncoder() {
        flow = Flowable.empty();
    }

    public CborEncoder merge(CborEncoder o) {
        flow = flow.concatWith(o.flow);
        return this;
    }

    public Flowable<ByteBuffer> observe() {
        return flow;
    }

    public Flowable<ByteBuffer> observe(int buffer_size) {
        return Flowable.generate(
                () -> {
                    Subscriber s = new Subscriber(buffer_size);
                    flow.concatWith(Flowable.just(ByteBuffer.allocate(0))).subscribe(s);
                    return s;
                },
                (s, emitter) -> {
                    ByteBuffer next = s.fill();
                    if (next == null) {
                        emitter.onComplete();
                    } else {
                        emitter.onNext(next);
                    }
                    return s;
                });
    }

    /**
     * cbor_encode_object will try to encode the object given as a parameter. The Object must be an
     * instance of one of the following class:
     * <lu>
     * <li>Double</li>
     * <li>Float</li>
     * <li>Long</li>
     * <li>Integer</li>
     * <li>Short</li>
     * <li>Byte</li>
     * <li>Boolean</li>
     * <li>String</li>
     * <li>Map</li>
     * <li>Collection</li>
     * <li>Object[]</li>
     * </lu>
     * <p>
     * <p>For Map, Collection and array, the encapsulated data must also be one of the listed type.
     *
     * @param o object to be encoded
     * @return this encoder
     * @throws BufferOverflowException  if the buffer is full
     * @throws CBOR.CborEncodingUnknown if object is not accepted type
     */
    public CborEncoder cbor_encode_object(Object o) throws CBOR.CborEncodingUnknown {
        if (o instanceof Double) {
            cbor_encode_double(((Double) o));
        } else if (o instanceof Float) {
            cbor_encode_float(((Float) o));
        } else if (o instanceof Number) {
            cbor_encode_int(((Number) o).longValue());
        } else if (o instanceof String) {
            cbor_encode_text_string((String) o);
        } else if (o instanceof Boolean) {
            cbor_encode_boolean((Boolean) o);
        } else if (o instanceof Map) {
            cbor_encode_map((Map) o);
        } else if (o instanceof Collection) {
            cbor_encode_collection((Collection) o);
        } else if (o != null) {
            Class<?> type = o.getClass();
            if (type.isArray()) {
                int len = Array.getLength(o);
                cbor_start_array(len);
                for (int i = 0; i < len; i++) {
                    cbor_encode_object(Array.get(o, i));
                }
            } else {
                throw new CBOR.CborEncodingUnknown();
            }
        } else {
            cbor_encode_null();
        }
        return this;
    }

    public CborEncoder cbor_encode_boolean(boolean b) {
        return encode_number((byte) (Constants.CborType.CborSimpleType), b ? Constants.CborSimpleValues.TrueValue : Constants.CborSimpleValues.FalseValue);
    }

    public CborEncoder cbor_encode_null() {
        return encode_number((byte) (Constants.CborType.CborSimpleType), Constants.CborSimpleValues.NullValue);
    }

    public CborEncoder cbor_encode_undefined() {
        return encode_number((byte) (Constants.CborType.CborSimpleType), Constants.CborSimpleValues.UndefinedValue);
    }

    /**
     * cbor_encode_collection will try to encode the collection given as a paremeter. The item
     * embedded in this collection must be encodable with cbor_encode_object.
     *
     * @param c collection to encode
     * @return this encoder
     * @throws BufferOverflowException  if the buffer is full
     * @throws CBOR.CborEncodingUnknown if object is not accepted type
     */
    public CborEncoder cbor_encode_collection(Collection c) throws CBOR.CborEncodingUnknown {
        cbor_start_array(c.size());
        for (Object o : c) {
            cbor_encode_object(o);
        }
        return this;
    }

    /**
     * cbor_encode_map will try to encode the map given as a paremeter. The keys and items
     * embedded in this map must be encodable with cbor_encode_object.
     *
     * @param m Map to encode
     * @return this encoder
     * @throws BufferOverflowException  if the buffer is full
     * @throws CBOR.CborEncodingUnknown if object is not accepted type
     */
    public CborEncoder cbor_encode_map(Map m) throws CBOR.CborEncodingUnknown {
        cbor_start_map(m.size());
        for (Object o : m.keySet()) {
            cbor_encode_object(o);
            cbor_encode_object(m.get(o));
        }
        return this;
    }


    /**
     * Starts an indefinite array. This encoder makes no check if a break ever appear later
     * so a later call to cbor_stop_array must be done to ensure cbor-validity.
     *
     * @return this encoder
     */
    public CborEncoder cbor_start_indefinite_array() {
        return cbor_start_array(-1);
    }

    /**
     * Starts an array of length given. if length is negative, the array is assumed to be of size
     * indefinite. This encoder makes no check if a break ever appear later so a later call to
     * cbor_stop_array must be done to ensure cbor-validity.
     *
     * @param length of the array
     * @return this encoder
     */
    public CborEncoder cbor_start_array(long length) {
        if (length < 0) {
            return put((byte) Constants.CborJumpTable.CborArrayWithIndefiniteLength);
        } else {
            return encode_number((byte) Constants.CborType.CborArrayType, length);
        }
    }

    /**
     * Close an opened array. This encoder makes no check wether a container was opened earlier.
     *
     * @return this encoder
     */
    public CborEncoder cbor_stop_array() {
        return put((byte) Constants.CborInternals.BreakByte);
    }

    /**
     * Starts a map of length given. if length is negative, the map is assumed to be of size
     * indefinite. This encoder makes no check if a break ever appear later so it must be added
     * manually to ensure cbor-validity.
     *
     * @param length of the map
     * @return this encoder
     */
    public CborEncoder cbor_start_map(long length) {
        if (length < 0) {
            return put((byte) Constants.CborJumpTable.CborMapWithIndefiniteLength);
        } else {
            return encode_number((byte) Constants.CborType.CborMapType, length);
        }
    }

    /**
     * Close an opened map. This encoder makes no check wether a container was opened earlier.
     *
     * @return this encoder
     */
    public CborEncoder cbor_stop_map() {
        return put((byte) Constants.CborInternals.BreakByte);
    }

    /**
     * Starts a byte string of length given. if length is negative, the string is assumed to be of
     * size indefinite. While this byte string is open, chunks must be added with
     * {@see cbor_put_byte_string_chunk}. This encoder makes no check if a break ever appear later
     * so it must be added manually to ensure cbor-validity.
     *
     * @param length of the string
     * @return this encoder
     */
    public CborEncoder cbor_start_byte_string(long length) {
        if (length < 0) {
            return put((byte) Constants.CborJumpTable.CborByteStringWithIndefiniteLength);
        } else {
            return encode_number((byte) Constants.CborType.CborByteStringType, length);
        }
    }

    /**
     * Add a fixed length byte string.
     *
     * @param chunk to add
     * @return this encoder
     */
    public CborEncoder cbor_put_byte_string_chunk(byte[] chunk) {
        return cbor_encode_byte_string(chunk);
    }

    /**
     * Close an opened byte string. This encoder makes no check wether a container was opened
     * earlier.
     *
     * @return this encoder
     */
    public CborEncoder cbor_stop_byte_string() {
        return put((byte) Constants.CborInternals.BreakByte);
    }

    /**
     * Starts a text string of length given. if length is negative, the string is assumed to be of
     * size indefinite. While this text string is open, chunks must be added with
     * {@see cbor_put_text_string_chunk}. This encoder makes no check if a break ever appear later
     * so it must be added manually to ensure cbor-validity.
     *
     * @param length of the string
     * @return this encoder
     */
    public CborEncoder cbor_start_text_string(long length) {
        if (length < 0) {
            return put((byte) Constants.CborJumpTable.CborTextStringWithIndefiniteLength);
        } else {
            return encode_number((byte) Constants.CborType.CborTextStringType, length);
        }
    }

    /**
     * Add a fixed length text string.
     *
     * @param chunk to add
     * @return this encoder
     */
    public CborEncoder cbor_put_text_string_chunk(String chunk) {
        return cbor_encode_text_string(chunk);
    }

    /**
     * Close an opened text string. This encoder makes no check wether a container was opened
     * earlier.
     *
     * @return this encoder
     */
    public CborEncoder cbor_stop_text_string() {
        return put((byte) Constants.CborInternals.BreakByte);
    }

    /**
     * Add a fixed length byte string.
     *
     * @param array to add
     * @return this encoder
     */
    public CborEncoder cbor_encode_byte_string(byte[] array) {
        return encode_string((byte) Constants.CborType.CborByteStringType, array);
    }

    /**
     * Add a fixed length byte string.
     *
     * @param buf to add
     * @return this encoder
     */
    public CborEncoder cbor_encode_byte_string(ByteBuffer buf) {
        return encode_string((byte) Constants.CborType.CborByteStringType, buf);
    }

    /**
     * Add a byte string from a Flowable whose size if undefined
     *
     * @param source to encode
     * @return this encoder
     */
    public CborEncoder cbor_encode_byte_string(Flowable<ByteBuffer> source) {
        cbor_start_byte_string(-1);
        add(source.map(ByteBuffer::duplicate));
        return this;
    }

    /**
     * Add a byte string from a Flowable whose size if undefined
     *
     * @param length of the byte string
     * @param source to encode
     * @return this encoder
     */
    public CborEncoder cbor_encode_byte_string(long length, Flowable<ByteBuffer> source) {
        cbor_start_byte_string(length);
        add(source.map(ByteBuffer::duplicate));
        return this;
    }

    /**
     * Add a fixed length text string. This encoder makes no check that the str supplied is
     * a UTF-8 text string.
     *
     * @param str to add
     * @return this encoder
     */
    public CborEncoder cbor_encode_text_string(String str) {
        return encode_string((byte) Constants.CborType.CborTextStringType, str.getBytes());
    }

    /**
     * add a tag to the CBOR stream.
     *
     * @param tag to add
     * @return this encoder
     */
    public CborEncoder cbor_encode_tag(long tag) {
        return encode_number((byte) Constants.CborType.CborTagType, tag);
    }

    /**
     * encode a double floating point number.
     *
     * @param value to add
     * @return this encoder
     */
    public CborEncoder cbor_encode_double(double value) {
        add(Flowable.create(s -> {
            ByteBuffer out = ByteBuffer.allocate(9);
            out.put((byte) Constants.CborJumpTable.CborDoublePrecisionFloat);
            out.putDouble(value);
            out.flip();
            s.onNext(out);
            s.onComplete();
        }, BackpressureStrategy.BUFFER));
        return this;
    }

    /**
     * encode a single floating point number.
     *
     * @param value to add
     * @return this encoder
     */
    public CborEncoder cbor_encode_float(float value) {
        add(Flowable.create(s -> {
            ByteBuffer out = ByteBuffer.allocate(5);
            out.put((byte) Constants.CborJumpTable.CborSinglePrecisionFloat);
            out.putFloat(value);
            out.flip();
            s.onNext(out);
            s.onComplete();
        }, BackpressureStrategy.BUFFER));
        return this;
    }

    /**
     * encode a half floating point number.
     *
     * @param value to add
     * @return this encoder
     */
    public CborEncoder cbor_encode_half_float(float value) {
        add(Flowable.create(s -> {
            ByteBuffer out = ByteBuffer.allocate(3);
            out.put((byte) Constants.CborJumpTable.CborHalfPrecisionFloat);
            out.putShort(halfPrecisionToRawIntBits(value));
            out.flip();
            s.onNext(out);
            s.onComplete();
        }, BackpressureStrategy.BUFFER));
        return this;
    }

    /**
     * add a simple value {@see Constants.CborSimpleValues}.
     *
     * @param value to add
     * @return this encoder
     */
    public CborEncoder cbor_encode_simple_value(byte value) {
        if ((value & 0xff) <= Constants.CborSimpleValues.Break) {
            return encode_number((byte) (Constants.CborMajorTypes.SimpleTypesType << Constants.CborInternals.MajorTypeShift), value);
        } else {
            return put((byte) Constants.CborJumpTable.CborSimpleValue1ByteFollow, value);
        }
    }

    /**
     * encode a positive or negative byte/short/integer/long number.
     *
     * @param value to add
     * @return this encoder
     */
    public CborEncoder cbor_encode_int(long value) {
        long ui = value >> 63;
        byte majorType = (byte) (ui & 0x20);
        ui ^= value;
        return encode_number(majorType, ui);
    }

    /**
     * encode an unsigned positive byte/short/integer/long number.
     *
     * @param ui to add
     * @return this encoder
     */
    public CborEncoder cbor_encode_uint(long ui) {
        return encode_number((byte) (Constants.CborMajorTypes.UnsignedIntegerType << Constants.CborInternals.MajorTypeShift), ui);
    }

    /**
     * encode a negative byte/short/integer/long number.
     *
     * @param absolute_value to add, value must be absolute
     * @return this encoder
     */
    public CborEncoder cbor_encode_negative_uint(long absolute_value) {
        return encode_number((byte) (Constants.CborMajorTypes.NegativeIntegerType << Constants.CborInternals.MajorTypeShift), absolute_value - 1);

    }

    private CborEncoder encode_string(byte shifted_mt, byte[] array) {
        int len = (array == null) ? 0 : array.length;
        if (array == null) {
            return encode_number(shifted_mt, len);
        } else {
            encode_number(shifted_mt, len);
            add(Flowable.create(s -> {
                s.onNext(ByteBuffer.wrap(array));
                s.onComplete();
            }, BackpressureStrategy.BUFFER));
            return this;
        }
    }

    private CborEncoder encode_string(byte shifted_mt, ByteBuffer buf) {
        int len = (buf == null) ? 0 : buf.remaining();
        if (buf == null) {
            return encode_number(shifted_mt, len);
        } else {
            encode_number(shifted_mt, len);
            add(Flowable.create(s -> {
                ByteBuffer dup = buf.duplicate();
                s.onNext(dup);
                s.onComplete();
            }, BackpressureStrategy.BUFFER));
            return this;
        }
    }

    private CborEncoder put(byte b) {
        add(Flowable.create(s -> {
            ByteBuffer out = ByteBuffer.allocate(1);
            out.put(b);
            out.flip();
            s.onNext(out);
            s.onComplete();
        }, BackpressureStrategy.BUFFER));
        return this;
    }

    private CborEncoder put(byte b1, byte b2) {
        add(Flowable.create(s -> {
            ByteBuffer out = ByteBuffer.allocate(2);
            out.put(b1);
            out.put(b2);
            out.flip();
            s.onNext(out);
            s.onComplete();
        }, BackpressureStrategy.BUFFER));
        return this;
    }

    private CborEncoder encode_number(final byte shifted_mt, final long ui) {
        add(Flowable.create(s -> {
            ByteBuffer out;
            if (ui < Constants.CborAdditionalInfo.Value8Bit) {
                out = ByteBuffer.allocate(1);
                out.put((byte) (shifted_mt | ui & 0xff));
            } else if (ui < 0x100L) {
                out = ByteBuffer.allocate(2);
                out.put((byte) (shifted_mt | Constants.CborAdditionalInfo.Value8Bit));
                out.put((byte) ui);
            } else if (ui < 0x10000L) {
                out = ByteBuffer.allocate(3);
                out.put((byte) (shifted_mt | Constants.CborAdditionalInfo.Value16Bit));
                out.putShort((short) ui);
            } else if (ui < 0x100000000L) {
                out = ByteBuffer.allocate(5);
                out.put((byte) (shifted_mt | Constants.CborAdditionalInfo.Value32Bit));
                out.putInt((int) ui);
            } else {
                out = ByteBuffer.allocate(9);
                out.put((byte) (shifted_mt | Constants.CborAdditionalInfo.Value64Bit));
                out.putLong(ui);
            }
            out.flip();
            s.onNext(out);
            s.onComplete();
        }, BackpressureStrategy.BUFFER));
        return this;
    }

    private short halfPrecisionToRawIntBits(float value) {
        int fbits = Float.floatToIntBits(value);
        int sign = (fbits >>> 16) & 0x8000;
        int val = (fbits & 0x7fffffff) + 0x1000;

        // might be or become NaN/Inf
        if (val >= 0x47800000) {
            if ((fbits & 0x7fffffff) >= 0x47800000) { // is or must become NaN/Inf
                if (val < 0x7f800000) {
                    // was value but too large, make it +/-Inf
                    return (short) (sign | 0x7c00);
                }
                return (short) ((sign | 0x7c00 | (fbits & 0x007fffff) >>> 13)); // keep NaN (and Inf) bits
            }
            return (short) (sign | 0x7bff); // unrounded not quite Inf
        }
        if (val >= 0x38800000) {
            // remains normalized value
            return (short) (sign | val - 0x38000000 >>> 13); // exp - 127 + 15
        }
        if (val < 0x33000000) {
            // too small for subnormal
            return (short) (sign); // becomes +/-0
        }

        val = (fbits & 0x7fffffff) >>> 23;
        // add subnormal bit, round depending on cut off and div by 2^(1-(exp-127+15)) and >> 13 | exp=0
        return (short) (sign | ((fbits & 0x7fffff | 0x800000) + (0x800000 >>> val - 102) >>> 126 - val));
    }


    private void add(Flowable<ByteBuffer> source) {
        flow = flow.concatWith(source);
    }

}