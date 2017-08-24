package org.rawkintrevo.cylon.common.solr

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

import org.apache.solr.client.solrj.{SolrClient, SolrQuery}
import org.apache.solr.client.solrj.SolrQuery.SortClause
import org.apache.mahout.math.scalabindings.MahoutCollections._
import org.apache.solr.client.solrj.response.QueryResponse
import org.apache.solr.common.SolrInputDocument

class CylonSolrClient {

  var solrClient: SolrClient = _

  def eigenFaceQuery(v: org.apache.mahout.math.Vector): QueryResponse = {
    val query = new SolrQuery
    query.setRequestHandler("/select")
    val currentPointStr = v.toArray.mkString(",")
    val eigenfaceFieldNames = (0 until v.size()).map(i => s"e${i}_d").mkString(",")
    val distFnStr = s"dist(2, ${eigenfaceFieldNames},${currentPointStr})"
    query.setQuery("*:*")
    query.setSort(new SortClause(distFnStr, SolrQuery.ORDER.asc))
    query.setFields("name_s", "calc_dist:" + distFnStr, "last_seen_pdt")
    query.setRows(10)

    val response: QueryResponse = solrClient.query(query)
    response
  }

  def insertNewFaceToSolr(v: org.apache.mahout.math.Vector): String = {
    val doc = new SolrInputDocument()
    val humanName = "human-" + scala.util.Random.alphanumeric.take(5).mkString("").toUpperCase
    doc.addField("name_s", humanName)
    doc.addField("last_seen_pdt", ZonedDateTime.now.format(DateTimeFormatter.ISO_INSTANT)) // YYYY-MM-DDThh:mm:ssZ   DateTimeFormatter.ISO_INSTANT, ISO-8601
    v.toMap.map { case (k, v) => doc.addField(s"e${k.toString}_d", v) }
    solrClient.add(doc)
    solrClient.commit()
    humanName
  }
}
