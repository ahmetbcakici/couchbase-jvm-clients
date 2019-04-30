//package com.couchbase.client.scala
//
//import java.util.UUID
//
//import com.couchbase.client.scala.codec.Conversions._
//import com.couchbase.client.scala.implicits.Codecs
//import com.fasterxml.jackson.databind.ObjectMapper
//import com.fasterxml.jackson.module.scala.DefaultScalaModule
//import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
//import com.github.plokhotnyuk.jsoniter_scala.macros.{CodecMakerConfig, JsonCodecMaker}
//
//import scala.util.{Failure, Success, Try}
//
//
////object Codecs2 {
////  def codec[T]: (Decodable[T], Encodable[T]) = (decoder[T], encoder[T])
////}
//import io.circe.generic.semiauto._
//
//case class Address(address: String)
//object Address {
//  // upickle requires adding this implicit thing to support conversion to/from JSON.  It is at least not much
//  // hassle to write.
//  implicit val rw: upickle.default.ReadWriter[Address] = upickle.default.macroRW
//  implicit val decoder: io.circe.Decoder[Address] = deriveDecoder[Address]
//  implicit val encoder: io.circe.Encoder[Address] = deriveEncoder[Address]
//}
//
// TODO get compiling

//case class User(name: String, age: Int, address: Seq[Address])
//object User {
//  implicit val rw: upickle.default.ReadWriter[User] = upickle.default.macroRW
//  implicit val codec: JsonValueCodec[User] = JsonCodecMaker.make[User](CodecMakerConfig())
//  implicit val cbCodec: Codec[User] = Codecs.codec[User]
//
//  implicit val decoder: io.circe.Decoder[User] = deriveDecoder[User]
//  implicit val encoder: io.circe.Encoder[User] = deriveEncoder[User]
//}
//
//
//class JsonSpec extends FlatSpec with Matchers with BeforeAndAfterAll with BeforeAndAfter {
//
//  private val DocId = "doc"
//  private
//    val cluster = Cluster.connect("localhost", "Administrator", "password")
//    val bucket = cluster.bucket("default")
//    val coll = bucket.defaultCollection
//
//
//  before {
//    // TODO BLOCKED (core) bucketManager
////    bucket.bucketManager().flush()
//    coll.remove(DocId)
//  }
//
//  "inserting upickle/ujson AST" should "succeed" in {
//    val content = ujson.Obj("value" -> "INSERTED")
//    assert(coll.insert(DocId, content).isSuccess)
//
//    (for {
//      doc <- coll.get(DocId)
//      content <- doc.contentAs[ujson.Obj]
//
//    } yield content) match {
//      case Success(result: ujson.Obj) =>
//        assert(result("value").str == "INSERTED")
//      case Failure(err) => assert(false)
//    }
//  }
//
//  "inserting case class with upickle/ujson via Array[Byte]" should "succeed" in {
//    import ujson.BytesRenderer
//    import upickle.default._
//
//    val content = User("John Smith", 29, List(Address("123 Fake Street")))
//
//    val bytes: Array[Byte] = transform(content).to(BytesRenderer()).toBytes
//
//    assert(coll.insert(DocId, bytes).isSuccess)
//
//    (for {
//      doc <- coll.get(DocId)
//      content <- doc.contentAs[ujson.Obj]
//
//    } yield content) match {
//      case Success(result: ujson.Obj) =>
//        assert(result("name").str == "John Smith")
//      case Failure(err) => assert(false)
//    }
//  }
//
//
//  "inserting case class with jsoniter via Array[Byte]" should "succeed" in {
//    import User._
//    import com.github.plokhotnyuk.jsoniter_scala.core._
//
//    val content = User("John Smith", 29, List(Address("123 Fake Street")))
//
//    assert(coll.insert(DocId, writeToArray(content)).isSuccess)
//
//    (for {
//      doc <- coll.get(DocId)
//      content <- Try(readFromArray(doc.contentAsBytes))
//
//    } yield content) match {
//      case Success(result: User) =>
//        assert(result.name == "John Smith")
//      case Failure(err) => assert(false)
//    }
//  }
//
//  "inserting case class with upickle/ujson via AST" should "succeed" in {
//    import upickle.default._
//
//    val content = User("John Smith", 29, List(Address("123 Fake Street")))
//
//    // use upickle to do the conversion
//    val encoded: ujson.Value = upickle.default.writeJs(content)
//
//    assert(coll.insert(DocId, encoded).isSuccess)
//
//    (for {
//      doc <- coll.get(DocId)
//      content <- Try(read[User](doc.contentAsBytes))
//
//    } yield content) match {
//      case Success(result: User) =>
//        assert(result.name == "John Smith")
//      case Failure(err) =>
//        assert(false)
//    }
//  }
//
//  "inserting case class directly" should "succeed" in {
//    val content = User("John Smith", 29, List(Address("123 Fake Street")))
//
//    assert(coll.insert(DocId, content).isSuccess)
//
//    (for {
//      doc <- coll.get(DocId)
//      content <- doc.contentAs[User]
//
//    } yield content) match {
//      case Success(result: User) =>
//        assert(result.name == "John Smith")
//      case Failure(err) =>
//        assert(false)
//    }
//  }
//
//  // TODO fails with Missing class: com/fasterxml/jackson/annotation/JsonMerge
//  "inserting case class with jackson" should "succeed" in {
//    val mapper = new ObjectMapper()
//    mapper.registerModule(DefaultScalaModule)
//
//    val content = User("John Smith", 29, List(Address("123 Fake Street")))
//
//
//    assert(coll.insert(DocId, mapper.writeValueAsBytes(content)).isSuccess)
//
//
//    (for {
//      doc <- coll.get(DocId)
//      content <- Try(mapper.readValue(doc.contentAsBytes, classOf[User]))
//
//    } yield content) match {
//      case Success(result: User) =>
//        assert(result.name == "John Smith")
//      case Failure(err) =>
//        println(err)
//        assert(false)
//    }
//  }
//
//  "inserting case class using circe AST" should "succeed" in {
//    import cats.syntax.show._
//    import io.circe._
//    import io.circe.syntax._
//
//    val content = User("John Smith", 29, Seq(Address("123 Fake Street")))
//    val json: io.circe.Json = content.asJson
//
//    coll.insert(DocId, json) match {
//      case Success(v) =>
//      case Failure(err) =>
//        println(err.getCause)
//        println(err.getCause.asInstanceOf[DecodingFailure].show)
//        assert(false)
//    }
//
//    (for {
//      doc <- coll.get(DocId)
//      content <- doc.contentAs[io.circe.Json]
//
//    } yield content) match {
//      case Success(result: io.circe.Json) =>
//        assert(result.hcursor.downField("name").as[String].right.get == "John Smith")
//      case Failure(err) =>
//        assert(false)
//    }
//  }
//
//  def docId(idx: Int): String = {
//    UUID.randomUUID().toString + "_" + idx
//  }
//}
