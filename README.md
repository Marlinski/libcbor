# libcbor 

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

## Parser Builder

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

They are also some other clever method such as do_here that enables the developper to perform some task when the parser has advanced to a certain state.


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


## License

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





