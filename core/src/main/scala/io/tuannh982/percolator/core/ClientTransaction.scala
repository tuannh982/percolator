package io.tuannh982.percolator.core

import scala.collection.mutable

class ClientTransaction(oracle: TimestampOracle, server: PercolatorServer) {

  private val startTs: Option[Timestamp]              = Some(getTimestampFromOracle)
  private val writeSet: mutable.TreeMap[Bytes, Bytes] = new mutable.TreeMap[Bytes, Bytes]()

  private def getTimestampFromOracle: Long = {
    val response = oracle.getTimestamp(GetTimestampRequest())
    response match {
      case GetTimestampResponse.Result(ts) => ts
    }
  }

  def set(key: Bytes, value: Bytes): Unit = {
    writeSet += key -> value
  }

  def get(key: Bytes): Option[Bytes] = {
    val startTs = this.startTs.getOrElse(throw new IllegalStateException("transaction not started"))
    val request = GetRequest(key, startTs)
    while (true) {
      val (otherTxStartTs, otherTxPrimary) = server.get(request) match {
        case GetResponse.LockedError(ts, primary) => (ts, primary)
        case GetResponse.Result(value)            => return value
      }
      // backoff
      Thread.sleep(100)
      // maybe cleanup lock
      val check = server.findWriteTimestamp(FindWriteTimestampRequest(otherTxPrimary, otherTxStartTs))
      check match {
        case FindWriteTimestampResponse.Result(commitTs) =>
          commitTs match {
            case Some(otherTxCommitTs) =>
              // we've found the write request, so other transaction is commited, do cleanup locks
              val _ = server.commit(CommitRequest(key, otherTxStartTs, otherTxCommitTs))
            case None =>
              // other transaction is not done, rollback other transaction
              val _ = server.rollback(RollbackRequest(key, otherTxStartTs))
          }
      }
    }
    throw new RuntimeException("unreachable")
  }

  def commit(): Boolean = {
    if (writeSet.isEmpty) {
      return true // nothing to commit
    }
    val startTs  = this.startTs.getOrElse(throw new IllegalStateException("transaction not started"))
    val commitTs = getTimestampFromOracle
    //
    val tuples           = writeSet.toIndexedSeq
    val primaryIndex     = 0 // using first key as primary
    val secondaryIndices = tuples.indices.filter(_ != primaryIndex)
    val primary          = tuples(primaryIndex)
    val secondaries      = secondaryIndices.map(tuples)
    // prewrite phase
    secondaries.foreach {
      case (key, value) =>
        val response = server.prewrite(PrewriteRequest(startTs, key, value, primary._1))
        response match {
          case PrewriteResponse.WriteConflictError(_) => return false
          case PrewriteResponse.LockedError(_)        => return false
          case PrewriteResponse.Success()             => ()
        }
    }
    // commit phase
    // commit primary key first
    server.commit(CommitRequest(primary._1, startTs, commitTs)) match {
      case CommitResponse.LockNotFound() => return false
      case CommitResponse.Success()      => ()
    }
    // then commit the secondary keys
    secondaries.foreach {
      case (key, _) =>
        // ignore secondary key errors, we don't care if it fail to commit secondary keys
        server.commit(CommitRequest(key, startTs, commitTs))
    }
    true
  }
}
