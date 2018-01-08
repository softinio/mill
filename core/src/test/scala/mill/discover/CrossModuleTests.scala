package mill.discover

import mill.{Module, T}
import mill.define.Cross
import mill.define.Segment
import mill.define.Segment.Label
import mill.util.TestUtil.test
import utest._
import mill.util.TestGraphs._
import mill.util.TestUtil
object CrossModuleTests extends TestSuite{

  val tests = Tests{

    'cross - {
      object outer extends TestUtil.BaseModule {
        object crossed extends mill.CrossModule[CrossedModule]("2.10.6", "2.11.8", "2.12.4")
        class CrossedModule(n: String) extends mill.Module{
          def scalaVersion = n
        }
      }

      val discovered = Discovered.make[outer.type]

      val Some((gen, innerMirror)) = discovered
        .mirror
        .children
        .head._2
        .asInstanceOf[Mirror[outer.type, outer.crossed.type]]
        .crossChildren

      val keys = gen(outer.crossed)
      assert(keys == List(List("2.10.6"), List("2.11.8"), List("2.12.4")))
      for(k <- keys){
        assert(outer.crossed(k:_*).scalaVersion == k.head)
      }
    }
    'doubleCross - {
      object outer extends TestUtil.BaseModule {
        val crossMatrix = for{
          platform <- Seq("", "sjs0.6", "native0.3")
          scalaVersion <- Seq("2.10.6", "2.11.8", "2.12.4")
          if !(platform == "native0.3" && scalaVersion == "2.10.6")
        } yield (platform, scalaVersion)
        object crossed extends mill.CrossModule[CrossModule](crossMatrix:_*)
        case class CrossModule(platform: String, scalaVersion: String) extends mill.Module{
          def suffix = Seq(scalaVersion, platform).filter(_.nonEmpty).map("_"+_).mkString
        }
      }

      val Some((gen, innerMirror)) = Discovered.make[outer.type]
        .mirror
        .children
        .head._2
        .asInstanceOf[Mirror[outer.type, outer.crossed.type]]
        .crossChildren

      val keys = gen(outer.crossed)
      val expectedKeys = List(
        List("2.10.6", ""),
        List("2.11.8", ""),
        List("2.12.4", ""),
        List("2.10.6", "sjs0.6"),
        List("2.11.8", "sjs0.6"),
        List("2.12.4", "sjs0.6"),
        List("2.11.8", "native0.3"),
        List("2.12.4", "native0.3")
      )

      assert(keys == expectedKeys)
      for(k <- keys){
        val suffix = outer.crossed(k:_*).suffix
        val expected = k.map(_.toString).filter(_.nonEmpty).map("_"+_).mkString
        assert(suffix == expected)
      }
    }
    'crossTargetDiscovery - {

      val discovered = Discovered.mapping(singleCross)

      val segments = discovered.targetsToSegments.values.toSet
      val expectedSegments = Set(
        List(Label("cross"), Segment.Cross(List("210")), Label("suffix")),
        List(Label("cross"), Segment.Cross(List("211")), Label("suffix")),
        List(Label("cross"), Segment.Cross(List("212")), Label("suffix"))
      )
      assert(segments == expectedSegments)
      val targets = discovered.targetsToSegments.keys.toSet
      val expected = Set(
        singleCross.cross("210").suffix,
        singleCross.cross("211").suffix,
        singleCross.cross("212").suffix
      )
      assert(targets == expected)
    }

    'doubleCrossTargetDiscovery - {
      val discovered = Discovered.mapping(doubleCross)
      val targets = discovered.targetsToSegments.keys.toSet

      val expected = Set(
        doubleCross.cross("jvm", "210").suffix,
        doubleCross.cross("js", "210").suffix,
        doubleCross.cross("jvm", "211").suffix,
        doubleCross.cross("js", "211").suffix,
        doubleCross.cross("jvm", "212").suffix,
        doubleCross.cross("js", "212").suffix,
        doubleCross.cross("native", "212").suffix
      )
      assert(targets == expected)
    }

    'nestedCrosses - {
      val discovered = Discovered.mapping(nestedCrosses).targetsToSegments
      assert(discovered.size == 9)
    }
  }
}

