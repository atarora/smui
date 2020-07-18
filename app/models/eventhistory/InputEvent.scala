package models.eventhistory

import java.sql.Connection
import java.time.LocalDateTime

import play.api.libs.json._

import anorm.SqlParser.get
import anorm._
import models.{Id, IdObject, eventhistory}
import models.input.{InputTag, SearchInput, SearchInputId, SearchInputWithRules}
import models.rules._

/**
  * @see evolutions/default/6.sql
  */
object SmuiEventType extends Enumeration {
  val CREATED = Value(0)
  val UPDATED = Value(1)
  val DELETED = Value(2)
  val VIRTUALLY_CREATED = Value(3)
}

class InputEventId(id: String) extends Id(id)
object InputEventId extends IdObject[InputEventId](new InputEventId(_))

case class InputEvent(
  id: InputEventId = InputEventId(),
  eventSource: String,
  eventType: Int,
  eventTime: LocalDateTime,
  userInfo: Option[String],
  inputId: String,
  jsonPayload: Option[String]) {

  import InputEvent._

  def toNamedParameters: Seq[NamedParameter] = Seq(
    ID -> id,
    EVENT_SOURCE -> eventSource,
    EVENT_TYPE -> eventType,
    EVENT_TIME -> eventTime,
    USER_INFO -> userInfo,
    INPUT_ID -> inputId,
    JSON_PAYLOAD -> jsonPayload
  )

}

object InputEvent {

  val TABLE_NAME = "input_event"
  val ID = "id"
  val EVENT_SOURCE = "event_source"
  val EVENT_TYPE = "event_type"
  val EVENT_TIME = "event_time"
  val USER_INFO = "user_info"
  val INPUT_ID = "input_id"
  val JSON_PAYLOAD = "json_payload"

  val sqlParser: RowParser[InputEvent] = {
    get[InputEventId](s"$TABLE_NAME.$ID") ~
      get[String](s"$TABLE_NAME.$EVENT_SOURCE") ~
      get[Int](s"$TABLE_NAME.$EVENT_TYPE") ~
      get[LocalDateTime](s"$TABLE_NAME.$EVENT_TIME") ~
      get[Option[String]](s"$TABLE_NAME.$USER_INFO") ~
      get[String](s"$TABLE_NAME.$INPUT_ID") ~
      get[Option[String]](s"$TABLE_NAME.$JSON_PAYLOAD") map {
        case id ~ eventSource ~ eventTypeRaw ~ eventTime ~ userInfo ~ inputId ~ jsonPayload =>
          InputEvent(id, eventSource, eventTypeRaw, eventTime, userInfo, inputId, jsonPayload)
    }
  }

  private def insert(eventSource: String, eventType: SmuiEventType.Value, userInfo: Option[String], inputId: String, jsonPayload: Option[String])(implicit connection: Connection): InputEvent = {

    // log ERROR, in case jsonPayload exceeds 5000 character limit (@see evolutions/default/6.sql)

    val event = InputEvent(
      id = InputEventId(),
      eventSource = eventSource,
      eventType = eventType.id,
      eventTime = LocalDateTime.now(),
      userInfo = userInfo,
      inputId = inputId,
      jsonPayload = jsonPayload
    )
    SQL(
      s"insert into $TABLE_NAME " +
        s"($ID, $EVENT_SOURCE, $EVENT_TYPE, $EVENT_TIME, $USER_INFO, $INPUT_ID, $JSON_PAYLOAD)" +
        s" values " +
        s"({$ID}, ${EVENT_SOURCE}, {$EVENT_TYPE}, {$EVENT_TIME}, {$USER_INFO}, {$INPUT_ID}, {$JSON_PAYLOAD})"
    ).on(event.toNamedParameters: _*).execute()
    event
  }

  def createForSearchInput(input: SearchInputWithRules, userInfo: Option[String], virtuallyCreated: Boolean)(implicit connection: Connection): InputEvent = {
    insert(
      "SearchInput",
      if (virtuallyCreated) SmuiEventType.VIRTUALLY_CREATED else SmuiEventType.CREATED,
      userInfo,
      input.id.id,
      Some(Json.toJson(input).toString())
    )
  }

  def updateForSearchInput(input: SearchInputWithRules, userInfo: Option[String])(implicit connection: Connection): InputEvent = {
    insert(
      "SearchInput",
      SmuiEventType.UPDATED,
      userInfo,
      input.id.id,
      Some(Json.toJson(input).toString())
    )
  }

  def deleteForSearchInput(inputId: SearchInputId, userInfo: Option[String])(implicit connection: Connection): InputEvent = {

    // write event for deletion of search input
    insert(
      "SearchInput",
      SmuiEventType.DELETED,
      userInfo,
      inputId.id,
      None
    )
  }

  // TODO create, update, deleteForCanonicalSpelling(...)

  def loadForId(inputId: String)(implicit connection: Connection): Seq[InputEvent] = {
    SQL"select * from #$TABLE_NAME where #$INPUT_ID = $inputId order by event_time asc".as(sqlParser.*)
  }

}