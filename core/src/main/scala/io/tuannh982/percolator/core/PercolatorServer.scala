package io.tuannh982.percolator.core

trait PercolatorServer {
  def get(request: GetRequest): GetResponse
  def prewrite(request: PrewriteRequest): PrewriteResponse
  def commit(request: CommitRequest): CommitResponse
  def rollback(request: RollbackRequest): RollbackResponse
  def findWriteTimestamp(request: FindWriteTimestampRequest): FindWriteTimestampResponse
}

// get
case class GetRequest(key: Bytes, ts: Timestamp)

sealed trait GetResponse

object GetResponse {
  case class LockedError(ts: Timestamp, primary: Bytes) extends GetResponse
  case class Result(value: Option[Bytes])               extends GetResponse
}

// prewrite

case class PrewriteRequest(startTs: Timestamp, key: Bytes, value: Bytes, primary: Bytes)

sealed trait PrewriteResponse

object PrewriteResponse {
  case class WriteConflictError(ts: Timestamp) extends PrewriteResponse
  case class LockedError(ts: Timestamp)        extends PrewriteResponse
  case class Success()                         extends PrewriteResponse
}

// commit

case class CommitRequest(key: Bytes, startTs: Timestamp, commitTs: Timestamp)

sealed trait CommitResponse

object CommitResponse {
  case class LockNotFound() extends CommitResponse
  case class Success()      extends CommitResponse
}

// rollback

case class RollbackRequest(key: Bytes, startTs: Timestamp)

sealed trait RollbackResponse

object RollbackResponse {
  case class Success() extends RollbackResponse
}

// find write timestamp

case class FindWriteTimestampRequest(key: Bytes, startTs: Timestamp)

sealed trait FindWriteTimestampResponse

object FindWriteTimestampResponse {
  case class Result(ts: Option[Timestamp]) extends FindWriteTimestampResponse
}
