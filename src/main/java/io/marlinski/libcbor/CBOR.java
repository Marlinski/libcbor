package io.marlinski.libcbor;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Map;

/**
 * @author Lucien Loiseau on 09/09/18.
 */
public class CBOR {

    public static class CborEncodingUnknown extends Exception {
    }

    public static CborEncoder encoder() {
        return new CborEncoder();
    }

    public static CborParser parser() {
        return CborParser.create();
    }

    /**
     * A decoded Cbor data item.
     */
    public static class DataItem {
        public int cborType;
        public Object item;
        public LinkedList<Long> tags;

        public DataItem(int cborType) {
            this.cborType = cborType;
            tags = new LinkedList<>();
        }

        public DataItem(int cborType, Object item) {
            this.cborType = cborType;
            setItem(null, item);
        }

        public DataItem(int cborType, Object item, LinkedList<Long> tags) {
            this.cborType = cborType;
            setTaggedItem(null, tags, item);
        }

        void addTags(LinkedList<Long> tags) {
            this.tags = tags;
        }

        void setItem(CborParser.ParserInCallback parser, Object item) {
            this.item = item;
        }

        void setTaggedItem(CborParser.ParserInCallback parser, LinkedList<Long> tags, Object item) {
            addTags(tags);
            setItem(null, item);
        }
    }

    public static class IntegerItem extends DataItem implements CborParser.ParseableItem {
        public IntegerItem() {
            super(Constants.CborType.CborIntegerType);
        }

        public IntegerItem(long l) {
            super(Constants.CborType.CborIntegerType, l);
        }

        public IntegerItem(LinkedList<Long> tags, long l) {
            super(Constants.CborType.CborIntegerType, l, tags);
        }

        public long value() {
            return (Long)item;
        }

        @Override
        public CborParser getItemParser() {
            return parser().cbor_parse_int(this::setTaggedItem);
        }

        @Override
        public boolean equals(Object o) {
            if(o == null) {
                return false;
            }
            if(o instanceof IntegerItem) {
                return ((IntegerItem) o).item.equals(this.item);
            }
            return false;
        }

        public static boolean ofType(DataItem item) {
            return item.cborType == Constants.CborType.CborIntegerType;
        }
    }

    public static class FloatingPointItem extends DataItem implements CborParser.ParseableItem {
        public FloatingPointItem() {
            super(Constants.CborType.CborDoubleType);
        }

        public FloatingPointItem(double d) {
            super(Constants.CborType.CborDoubleType, d);
        }

        public FloatingPointItem(LinkedList<Long> tags, double d) {
            super(Constants.CborType.CborDoubleType, d, tags);
        }

        public double value() {
            return (Double)item;
        }

        @Override
        public CborParser getItemParser() {
            return parser().cbor_parse_float(this::setTaggedItem);
        }

        @Override
        public boolean equals(Object o) {
            if(o == null) {
                return false;
            }
            if(o instanceof FloatingPointItem) {
                return ((FloatingPointItem) o).item.equals(this.item);
            }
            return false;
        }

        public static boolean ofType(DataItem item) {
            return item.cborType == Constants.CborType.CborDoubleType;
        }
    }

    public static class ByteStringItem extends DataItem implements CborParser.ParseableItem {
        public ByteStringItem() {
            super(Constants.CborType.CborByteStringType);
        }

        public ByteStringItem(ByteBuffer b) {
            super(Constants.CborType.CborByteStringType, b);
        }

        public ByteStringItem(LinkedList<Long> tags, ByteBuffer b) {
            super(Constants.CborType.CborByteStringType, b, tags);
        }

        public ByteBuffer value() {
            return (ByteBuffer)item;
        }

        @Override
        public CborParser getItemParser() {
            return parser().cbor_parse_byte_string_unsafe(this::setTaggedItem);
        }

        @Override
        public boolean equals(Object o) {
            if(o == null) {
                return false;
            }
            if(o instanceof ByteStringItem) {
                return ((ByteStringItem) o).item.equals(this.item);
            }
            return false;
        }

        public static boolean ofType(DataItem item) {
            return item.cborType == Constants.CborType.CborByteStringType;
        }
    }

    public static class TextStringItem extends DataItem implements CborParser.ParseableItem {
        public TextStringItem() {
            super(Constants.CborType.CborTextStringType);
        }

        public TextStringItem(String str) {
            super(Constants.CborType.CborTextStringType, str);
        }

        public TextStringItem(LinkedList<Long> tags, String str) {
            super(Constants.CborType.CborTextStringType, str, tags);
        }

        public String value() {
            return (String)item;
        }

        @Override
        public CborParser getItemParser() {
            return parser().cbor_parse_text_string_unsafe(this::setTaggedItem);
        }

        @Override
        public boolean equals(Object o) {
            if(o == null) {
                return false;
            }
            if(o instanceof String) {
                return item.equals(o);
            }
            if(o instanceof TextStringItem) {
                return ((TextStringItem) o).item.equals(this.item);
            }
            return false;
        }

        public static boolean ofType(DataItem item) {
            return item.cborType == Constants.CborType.CborTextStringType;
        }
    }

    public static class ArrayItem extends DataItem implements CborParser.ParseableItem {
        public ArrayItem() {
            super(Constants.CborType.CborArrayType);
        }

        public ArrayItem(Collection c) {
            super(Constants.CborType.CborArrayType, c);
        }

        public ArrayItem(LinkedList<Long> tags, Collection c) {
            super(Constants.CborType.CborArrayType, c, tags);
        }

        public Collection value() {
            return (Collection) item;
        }

        @Override
        public CborParser getItemParser() {
            // todo
            return null;
        }

        @Override
        public boolean equals(Object o) {
            if(o == null) {
                return false;
            }
            if(o instanceof ArrayItem) {
                return ((ArrayItem) o).item.equals(this.item);
            }
            return false;
        }

        public static boolean ofType(DataItem item) {
            return item.cborType == Constants.CborType.CborArrayType;
        }
    }

    public static class MapItem extends DataItem implements CborParser.ParseableItem {
        public MapItem() {
            super(Constants.CborType.CborMapType);
        }

        public MapItem(Map m) {
            super(Constants.CborType.CborMapType, m);
        }

        public MapItem(LinkedList<Long> tags, Map m) {
            super(Constants.CborType.CborMapType, m, tags);
        }

        public Map value() {
            return (Map)item;
        }

        @Override
        public CborParser getItemParser() {
            // todo
            return null;
        }

        @Override
        public boolean equals(Object o) {
            if(o == null) {
                return false;
            }
            if(o instanceof MapItem) {
                return ((MapItem) o).item.equals(this.item);
            }
            return false;
        }

        public static boolean ofType(DataItem item) {
            return item.cborType == Constants.CborType.CborMapType;
        }
    }

    public static class TagItem extends DataItem implements CborParser.ParseableItem {
        public TagItem() {
            super(Constants.CborType.CborTagType);
        }

        public TagItem(long l) {
            super(Constants.CborType.CborTagType, l);
        }

        public long value() {
            return (Long)item;
        }

        @Override
        public CborParser getItemParser() {
            return parser().cbor_parse_tag(this::setItem);
        }

        @Override
        public boolean equals(Object o) {
            if(o == null) {
                return false;
            }
            if(o instanceof TagItem) {
                return ((TagItem) o).item.equals(this.item);
            }
            return false;
        }

        public static boolean ofType(DataItem item) {
            return item.cborType == Constants.CborType.CborTagType;
        }
    }

    public static class SimpleValueItem extends DataItem implements CborParser.ParseableItem {
        SimpleValueItem() {
            super(Constants.CborType.CborSimpleType);
        }

        SimpleValueItem(int value) {
            super(Constants.CborType.CborSimpleType);
            setItem(null, value);
        }

        SimpleValueItem(LinkedList<Long> tags, int value) {
            super(Constants.CborType.CborSimpleType, value, tags);
        }

        public long value() {
            return (Long)item;
        }

        @Override
        public CborParser getItemParser() {
            return parser().cbor_parse_simple_value(this::setItem);
        }

        @Override
        public boolean equals(Object o) {
            if(o == null) {
                return false;
            }
            if(o instanceof SimpleValueItem) {
                return this.item.equals(((SimpleValueItem) o).item);
            }
            return false;
        }

        public static boolean ofType(DataItem item) {
            return item.cborType == Constants.CborType.CborSimpleType;
        }
    }

    public static class BooleanItem extends DataItem implements CborParser.ParseableItem {
        public BooleanItem() {
            super(Constants.CborType.CborBooleanType);
        }

        public BooleanItem(boolean b) {
            super(Constants.CborType.CborBooleanType, b);
        }

        public boolean value() {
            return (Boolean)item;
        }

        @Override
        public CborParser getItemParser() {
            return parser().cbor_parse_boolean(this::setItem);
        }

        @Override
        public boolean equals(Object o) {
            if(o == null) {
                return false;
            }
            if(o instanceof Boolean) {
                return item.equals(o);
            }
            if(o instanceof BooleanItem) {
                return ((BooleanItem) o).item.equals(this.item);
            }
            return false;
        }

        public static boolean ofType(DataItem item) {
            return item.cborType == Constants.CborType.CborBooleanType;
        }
    }

    public static class NullItem extends DataItem implements CborParser.ParseableItem {
        public NullItem() {
            super(Constants.CborType.CborNullType);
        }

        @Override
        public CborParser getItemParser() {
            return parser().cbor_parse_null();
        }

        public Object value() {
            return null;
        }

        @Override
        public boolean equals(Object o) {
            if(o == null) {
                return false;
            }
            if(o instanceof NullItem) {
                return true;
            }
            return false;
        }

        public static boolean ofType(DataItem item) {
            return item.cborType == Constants.CborType.CborNullType;
        }
    }

    public static class UndefinedItem extends DataItem implements CborParser.ParseableItem {
        public UndefinedItem() {
            super(Constants.CborType.CborUndefinedType);
        }

        @Override
        public CborParser getItemParser() {
            return parser().cbor_parse_undefined();
        }

        @Override
        public boolean equals(Object o) {
            if(o == null) {
                return false;
            }
            if(o instanceof UndefinedItem) {
                return true;
            }
            return false;
        }

        public static boolean ofType(DataItem item) {
            return item.cborType == Constants.CborType.CborUndefinedType;
        }
    }

    public static class BreakItem extends DataItem implements CborParser.ParseableItem {
        public BreakItem() {
            super(Constants.CborType.CborBreakType);
        }

        @Override
        public CborParser getItemParser() {
            return parser().cbor_parse_break();
        }

        @Override
        public boolean equals(Object o) {
            if(o == null) {
                return false;
            }
            if(o instanceof BreakItem) {
                return true;
            }
            return false;
        }

        public static boolean ofType(DataItem item) {
            return item.cborType == Constants.CborType.CborBreakType;
        }
    }

}
