package io.tuannh982.percolator.core

import io.tuannh982.percolator.core.KvStorage.{Column, RowUpdate, TimeRange}

// in practical implementation, the server should handle request of single key sequentially to make sure
// that will have no write conflict
class PercolatorServerImpl(table: KvStorage) extends PercolatorServer {

  override def get(request: GetRequest): GetResponse = {
    // Check if the row we are going to read is locked with a timestamp in the range [0, ts]
    val lock = table.readLatest(request.key, Column.Lock, TimeRange(0, request.ts))
    lock match {
      case Some((ts, primary)) => return GetResponse.LockedError(ts, primary)
      case None                => ()
    }
    // Get the latest record in the row’s write column whose commit_ts is in range [0, ts].
    // The record contains the start_ts of the transaction when it was committed
    val write = table.readLatest(request.key, Column.Write, TimeRange(0, request.ts))
    val startTs = write match {
      case None               => return GetResponse.Result(None)
      case Some((_, startTs)) => startTs
    }
    // Get the row’s value in the data column whose timestamp is exactly start_ts. Then the value is what we want.
    val value = table.read(request.key, Column.Data, startTs)
    GetResponse.Result(value.map(_._2))
  }

  override def prewrite(request: PrewriteRequest): PrewriteResponse = {
    // If there’s already a lock or newer version than start_ts,
    // the current transaction will be rolled back because of write conflict
    val write = table.readLatest(request.key, Column.Write, TimeRange(request.startTs, Long.MaxValue))
    write match {
      case Some((ts, _)) => return PrewriteResponse.WriteConflictError(ts)
      case None          => ()
    }
    // If there’s already a lock or newer version than start_ts,
    // the current transaction will be rolled back because of write conflict
    val lock = table.readLatest(request.key, Column.Lock, TimeRange(0, Long.MaxValue))
    lock match {
      case Some((ts, _)) => return PrewriteResponse.LockedError(ts)
      case None          => ()
    }
    // put a lock in the lock column and write the value to the data column with the timestamp start_ts
    val writes = Seq(
      RowUpdate.Write(request.startTs, Column.Data, request.value),
      RowUpdate.Write(request.startTs, Column.Lock, request.primary)
    )
    table.update(request.key, writes)
    PrewriteResponse.Success()
  }

  override def commit(request: CommitRequest): CommitResponse = {
    // Remove the primary lock, and at the same time write a record to the write column with timestamp commit_ts,
    // whose value records the transaction’s start_ts. If the primary lock is missing, the commit fails.
    val read = table.read(request.key, Column.Lock, request.startTs)
    if (read.isEmpty) {
      return CommitResponse.LockNotFound()
    }
    val writes = Seq(
      RowUpdate.Write(request.commitTs, Column.Write, request.startTs),
      RowUpdate.Delete(request.startTs, Column.Lock)
    )
    table.update(request.key, writes)
    CommitResponse.Success()
  }

  override def rollback(request: RollbackRequest): RollbackResponse = {
    val writes = Seq(
      RowUpdate.Delete(request.startTs, Column.Lock)
    )
    table.update(request.key, writes)
    RollbackResponse.Success()
  }

  override def findWriteTimestamp(request: FindWriteTimestampRequest): FindWriteTimestampResponse = {
    val write = table.findWriteTimestamp(request.key, request.startTs)
    FindWriteTimestampResponse.Result(write)
  }
}
