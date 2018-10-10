# libcbor 

[![](https://jitpack.io/v/RightMesh/libcbor.svg)](https://jitpack.io/#RightMesh/libcbor)

A small library for encoding and parsing CBOR objects. It follows the CBOR specification defined in the RFC 7049.
This library is specially useful to encode/decode data in the context of a network protocol. You can use this library
in your project with gradle using jitpack:

```java
repositories {
    maven { url 'https://jitpack.io' }
}
```

```java
dependencies {
   implementation 'com.github.RightMesh:libcbor:v1.0'
}
```


# Features

* Encodes and decodes **all examples** described in RFC 7049
* Supports 64-bit integer values
* Reusable Encoder 
* Reactive parsing (callback method for every decoded items)
* Advanced parser builder 
* Conditional parsing
* Runtime parser manipulation

# Encoding Example

## Encoder Builder

The following is a simple encoding of a CBOR packet header that contains only 3 fields:

```java
Header header = Header.create();
CborEncoder enc = CBOR.encoder()
                .cbor_start_array(3)
                .cbor_encode_int(header.version)
                .cbor_encode_int(header.flag)
                .cbor_encode_int(header.sequence);
```

The following is an encoding of a similar CBOR packet header but with two additional items:

```java
Header header = Header.create();
CborEncoder enc = CBOR.encoder()
                .cbor_start_array(3)
                .cbor_encode_int(header.version)
                .cbor_encode_int(header.flag)
                .cbor_encode_int(header.sequence);
                .merge(encodePeer(header.destination))
                .merge(encodePeer(header.source));

public CborEncoder encodePeer(Peer peer) {
    return CBOR.encoder()
                .cbor_start_array(2)
                .cbor_encode_text_string(peer.id)
                .cbor_encode_int(peer.port);
}
```

In this example, encodePeer returns a CborEncoder that can be merged into our first builder with the keyword **merge**.

## Generating the encoded data stream

We use the method **observe()** on the CborEncoder to generate a Flowable of ByteBuffer like so:

```java
    Flowable<ByteBuffer> encoded = enc.observe();
```

By default, each ByteBuffer will be of different size, possibly zero and may not be optimised to be sent over the network. 
Alternatively, you can add a buffer size argument like so:

```java
    Flowable<ByteBuffer> encoded = enc.observe(2048);
```

In this case, each ByteBuffer will be of size 2048, the same ByteBuffer will be reused for every call.

# Decoding Example

## Build the Parser

The following shows an example of a simple parser to later decode the first header encoded above:

```java
Header header = new Header();
CborParser parser = CBOR.parser()
                    .cbor_parse_int((__, ___, v) -> header.version = v)
                    .cbor_parse_int((__, ___, f) -> header.flag = f)
                    .cbor_parse_int((__, ___, s) -> header.seq = s);
```

For every parsed item, a callback method is called. Though the callback has a different signature according to what item
is parsed, most callback usually have at least three parameters:

* A ParserInCallback pointer which can be used to modify the parser on runtime as during decoding 
* A list of CBOR tags that were preceding the parsed item
* the parsed item (it can be a long, float, array, map, or a custom item)

Check the source to see a list of all the method to parse primitive items.


## Consuming buffer to be parsed

A CborParser can consumes buffer by calling read like so:

```java
public void onRecvBuffer(ByteBuffer buffer) {
    while(buffer.hasRemaining()) {
        if(parser.read(buffer)) {
            parser.reset();
        }
    }
}
```

read() takes a ByteBuffer as a parameter and will try to parse the buffer as dictated by the CborParser and will advance the ByteBuffer position
accordingly. It returns false if more buffer is required and true if the entire parsing sequence has been finished. It will not advance the bytebuffer
position more than what it actually decoded, it is thus possible that the CborParser returns true but more data are still available in the bytebuffer.
If the buffer stream is a series of similar CBOR pattern, the CborParser can simply be reset and be reused like in the example above.


# Parser Special Features

## parse custom item: cbor_parse_custom_item

It is possible to parse a CustomItem by Implementing CborParser.ParseableItem. Such class must implement the getItemparser()
that returns a CborParser object used to decode the custom item:

```java
class PeerItem implements CborParser.ParseableItem {
    Peer peer;

    @Override
    public CborParser getItemParser() {
        return CBOR.parser()
                .cbor_open_array((__, ___, i) -> {
                    if (i != 2) {
                        throw new RxParserException("wrong number of element in primary block");
                    } else {
                        this.peer = new Peer();
                    }
                })
                .cbor_parse_text_string_full((__, id) -> peer.id = id)
                .cbor_parse_int((__, ___, i) -> peer.port = i);
    }
}
```
We can use PeerItem like so:

```java
Header header = new Header();
CborParser parser = CBOR.parser()
                    .cbor_parse_int((__, ___, v) -> header.version = v)
                    .cbor_parse_int((__, ___, f) -> header.flag = f)
                    .cbor_parse_int((__, ___, s) -> header.seq = s)
                    .cbor_parse_custom_item(PeerItem::new, (__, ___, item) -> header.destination = item.peer)
                    .cbor_parse_custom_item(PeerItem::new, (__, ___, item) -> header.destination = item.peer);
```

cbor_parse_custom_item requires two parameters:

* a Factory to the parseable item
* a callback 

On runtime, when the parser state machine advance to the custom item call, it instantiate a new PeerItem using the factory and invoke getItemParser()
and uses this parser to parse the data. When the data is parsed, the callback is called at which point we can retrieve the parsed Peer.

## Conditional Parsing: do_insert_if

Additionally with libcbor, it is possible to do conditional parsing. For instance in the example above, the flag may 
give an hint wether or not we must parsed the two peer item (source and destination). We can perform conditional parsing like so:

```java
Header header = new Header();
CborParser parser = CBOR.parser()
                    .cbor_parse_int((__, ___, v) -> header.version = v)
                    .cbor_parse_int((__, ___, f) -> header.flag = f)
                    .cbor_parse_int((__, ___, s) -> header.seq = s)
                    .do_insert_if(
                        (__) -> (header.flag == FLAG_HEADER_CONTAINS_DESTINATION),
                        CBOR.parser().cbor_parse_custom_item(PeerItem::new, (__, ___, item) -> header.destination = item.peer))
                    .do_insert_if(
                        (__) -> (header.flag == FLAG_HEADER_CONTAINS_SOURCE),
                        CBOR.parser().cbor_parse_custom_item(PeerItem::new, (__, ___, item) -> header.destination = item.peer))
```

the do_insert_if call requires two parameters:

* a CallbackCondition that is called on runtime and should return a boolean
* A CborParser that will be inserted if the CallbackCondition returns true

You can also insert a parser from within a callback using **do_insert_now**:

```java
Header header = new Header();
CborParser parser = CBOR.parser()
                    .cbor_parse_int((__, ___, v) -> header.version = v)
                    .cbor_parse_int((__, ___, f) -> header.flag = f)
                    .cbor_parse_int((p, ___, s) -> {
                        header.seq = s
                        if (header.flag == FLAG_HEADER_CONTAINS_DESTINATION) {
                            p.insert_now(
                                CBOR.parser().cbor_parse_custom_item(PeerItem::new, (__, ___, item) -> header.destination = item.peer)
                            );
                        }
                    });
```


## Run tasks: do_here

Anywhere in the parsing sequence, you can use **do_here** to perform a certain task, 
you may for instance print info whenever the parsing start and when it is finish:

```java
Header header = new Header();
CborParser parser = CBOR.parser()
                    .do_here((__) -> System.out.println("parsing started");
                    .cbor_parse_int((__, ___, v) -> header.version = v)
                    .cbor_parse_int((__, ___, f) -> header.flag = f)
                    .cbor_parse_int((__, ___, s) -> header.seq = s)
                    .do_here((__) -> System.out.println("parsing finish");
```

## Process every parsed buffer:  do_for_each and undo_for_each

You can use **do_for_each** to run a certain task for every buffer that was successfully parsed and **undo_for_each** to stop.
For instance, say you are parsing a packet header but you also need to perform a CRC for every buffer that belongs to this header
and compare it to the CRC that is right after the header:

```java
HeaderWithCRC header = new HeaderWithCRC();
CborParser parser = CBOR.parser()
                    .do_here((__) -> crc.init());
                    .do_for_each("crc-16", (__, buffer) -> crc.read(buffer));
                    .cbor_parse_int((__, ___, v) -> header.version = v)
                    .cbor_parse_int((__, ___, f) -> header.flag = f)
                    .cbor_parse_int((__, ___, s) -> header.seq = s)
                    .undo_for_each("crc-16");
                    .cbor_parse_byte_string(
                        (parser, tags, size) -> {},
                        (parser, tags, buffer) -> crc.check(buffer)); 
```

**do_for_each** takes two argument:
* A String that is uses as a key to identify this specific task, it is used to disable it with **undo_for_each**
* A Callback that takes a ParserInCallback and a buffer as a parameter

Alternatively, you can also trigger **do_for_each** from within a callback to react to a value that was just parsed. 
For instance a boolean that indicate weter it is CRC16 or CRC32, in that case we would start consumming both CRC
but will disable one of them as soon as we know which one we need:

```java
HeaderWithCRC header = new HeaderWithCRC();
CRC16 crc16 = new CRC16();
CRC32 crc32 = new CRC32();

CborParser parser = CBOR.parser()
                    .do_here((__) -> {
                        crc16.init());
                        crc32.init());
                    })
                    .do_for_each("crc-16", (__, buffer) -> crc16.read(buffer));
                    .do_for_each("crc-32", (__, buffer) -> crc32.read(buffer));
                    .cbor_parse_int((__, ___, v) -> header.version = v)
                    .cbor_parse_int((__, ___, f) -> header.flag = f)
                    .cbor_parse_boolean((p, b) ->
                        if(b) {
                            p.undo_for_each_now("crc-16");
                        } else {
                            p.undo_for_each_now("crc-32");
                        }
                    .cbor_parse_int((__, ___, s) -> header.seq = s)
```

## Access parsed item from downstream: save() and get()

If a parsed value is needed later in the parsing sequence, you can either save it in a variable outside of the parsing sequence, or use
parserInCallback.save() and retrieve it later with get(). For instance in the previous example, we need to check the value of the flag
to decide which crc to check:

```java
HeaderWithCRC header = new HeaderWithCRC();
CRC16 crc16 = new CRC16();
CRC32 crc32 = new CRC32();

CborParser parser = CBOR.parser()
                    .do_here((__) -> {
                        crc16.init());
                        crc32.init());
                    })
                    .do_for_each("crc-16", (__, buffer) -> crc16.read(buffer));
                    .do_for_each("crc-32", (__, buffer) -> crc32.read(buffer));
                    .cbor_parse_int((__, ___, v) -> header.version = v)
                    .cbor_parse_int((__, ___, f) -> header.flag = f)
                    .cbor_parse_boolean((p, b) ->
                        if(b) {
                            p.undo_for_each_now("crc-16");
                        } else {
                            p.undo_for_each_now("crc-32");
                        }
                    .cbor_parse_int((__, ___, s) -> header.seq = s)
                    .cbor_parse_byte_string(
                        (p, tags, size) -> {},
                        (p, tags, buffer) -> {
                            if(p.<Boolean>get(b)) {
                                crc32.check(buffer);
                            } else {
                                crc16.check(buffer);
                            }
                        });
```

**save** takes two parameters:
* The key to identify the Object to be saved
* the object itself

**get** takes only one parameter that is the key used to save the object. By default it returns an Object but you can use 
the template <T> to force cast the object and avoid a manual casting. No check is done to ensure that the object saved is of 
the same type.

Knowing that, every variable could be processed in-parser and would not require any variable outside, the final example would be a complete
self-contained parser:

```java

CBOR.parser()
    .do_here((p) -> {
            p.save("header", new HeaderWithCRC());
            p.save("crc16", CRC16.create());
            p.save("crc32", CRC32.create());
            })
    .do_for_each("crc16consumer", (p, buffer) -> p.<CRC16>get("crc16").read(buffer))
    .do_for_each("crc32consumer", (p, buffer) -> p.<CRC16>get("crc32").read(buffer));
    .cbor_parse_int((__, ___, v) -> header.version = v)
    .cbor_parse_int((__, ___, f) -> header.flag = f)
    .cbor_parse_boolean((p, b) ->
            if(b) {
                p.undo_for_each_now("crc16consumer");
            } else {
                p.undo_for_each_now("crc32consumer");
            })
    .cbor_parse_int((__, ___, s) -> header.seq = s)
    .undo_for_each("crc16consumer")  // one of them was already disabled but it doesn't matter
    .undo_for_each("crc32consumer")
    .cbor_parse_byte_string(
            (parser, tags, size) -> {},
            (parser, tags, buffer) -> {
                if(parser.<Boolean>get(b)) {
                    p.<CRC16>get("crc32").check(buffer);
                } else {
                    p.<CRC16>get("crc32").check(buffer);
                }
            });
```


# License

    Copyright 2018 Lucien Loiseau

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.





