package io.tuannh982.percolator

package object core {
  type Bytes     = Array[Byte]
  type Timestamp = Long

  implicit val bytesOrd: Ordering[Bytes] = new Ordering[Bytes] {

    override def compare(x: Bytes, y: Bytes): Int = {
      val lengthCmp = x.length.compare(y.length)
      if (lengthCmp != 0) {
        lengthCmp
      } else {
        val end = x.length min y.length
        for (i <- 0 until end) {
          val byteCmp = x(i).compare(y(i))
          if (byteCmp != 0) {
            return byteCmp
          }
        }
        0
      }
    }
  }
}
