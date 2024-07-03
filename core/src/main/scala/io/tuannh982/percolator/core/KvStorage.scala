package io.tuannh982.percolator.core

import io.tuannh982.percolator.core.KvStorage.{Column, RowUpdate, TimeRange}

trait KvStorage {
  // read key of given timestamp
  def read[T](key: Bytes, column: Column[T], ts: Timestamp): Option[(Timestamp, T)]
  // read latest key of given time range
  def readLatest[T](key: Bytes, column: Column[T], range: TimeRange): Option[(Timestamp, T)]
  // find the timestamp Write record with given start_ts
  def findWriteTimestamp(key: Bytes, startTs: Timestamp): Option[Timestamp]
  // update a key
  def update(key: Bytes, updates: Seq[RowUpdate]): Unit
}

object KvStorage {
  case class TimeRange(from: Timestamp, to: Timestamp)

  sealed trait Column[T]

  object Column {
    object Data  extends Column[Bytes]
    object Lock  extends Column[Bytes]
    object Write extends Column[Timestamp]
  }

  sealed trait RowUpdate

  object RowUpdate {
    case class Write[T](ts: Timestamp, column: Column[T], value: T) extends RowUpdate
    case class Delete(ts: Timestamp, column: Column[_])             extends RowUpdate
  }
}
