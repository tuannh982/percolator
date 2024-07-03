package io.tuannh982.percolator.core

trait TimestampOracle {
  def getTimestamp(request: GetTimestampRequest): GetTimestampResponse
}

// get timestamp
case class GetTimestampRequest()

sealed trait GetTimestampResponse

object GetTimestampResponse {
  case class Result(ts: Timestamp) extends GetTimestampResponse
}
