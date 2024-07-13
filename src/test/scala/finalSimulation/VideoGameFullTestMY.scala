package videogamedb.finalsimulation

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import scala.util.Random

class VideoGameFullTest extends Simulation {

  val httpProtocol = http.baseUrl("https://videogamedb.uk/api")
    .acceptHeader("application/json")
    .contentTypeHeader("application/json")

  // Variables block
  def USERS_COUNT = System.getProperty("USER_COUNT", "5").toInt
  def RAMP_UP_TIME = System.getProperty("RAMP_UP_TIME", "10").toInt
  def TEST_DURATION = System.getProperty("TEST_DURATION", "10").toInt

  // Custom feeder block
  val idNumbers = (1 to 10).iterator
  val rnd = new Random()
  val now = LocalDate.now()
  val pattern = DateTimeFormatter.ofPattern("yyy-MM-dd")

  def randomString(lenght: Int) = {
    rnd.alphanumeric.filter(_.isLetter).take(lenght).mkString
  }

  def getRandomDate(startDate: LocalDate, random: Random): String = {
    startDate.minusDays(random.nextInt(30)).format(pattern)
  }

  val customFeeder = Iterator.continually(Map(
    "gameId" -> idNumbers.next(),
    "name" -> ("Game - " + randomString(5)),
    "releaseDate" -> getRandomDate(now, rnd),
    "reviewScore" -> rnd.nextInt(100),
    "category" -> ("Category - " + randomString(6)),
    "rating" -> ("Rating - " + randomString(4))
  ))

  // CSV feeder
  val csvFeeder = csv("data/gameCsvFile.csv").circular

  before {
    println(s"Run test with ${USERS_COUNT} users.")
    println(s"Run-up time of test run is ${RAMP_UP_TIME}")
    println(s"Test duration for test is ${TEST_DURATION}")
  }

  // Http methods
  def authenticate(pause: Int, login: String, pass: String) = {
    exec(
      http("Authenticate")
        .post("/authenticate")
        .body(StringBody(s"{\n  \"password\": \"${pass}\",\n  \"username\": \"${login}\"\n}"))
        .check(jsonPath("$.token").saveAs("jwtToken"))
    ).pause(pause)
  }

  def getAllVideoGame(pause: Int) = {
    exec(
      http("Get all video game")
        .get("/videogame")
        .check(status.is(200))
    ).pause(pause)
  }

  def createNewGame(pause: Int, repeatNumber: Int) = {
    repeat(repeatNumber) {
      feed(customFeeder)
        .exec(http("create new game - #{name}")
          .post("/videogame")
          .header("authorization", "Bearer #{jwtToken}")
          .body(ElFileBody("bodies/newGameTemplate.json")).asJson
          .check(bodyString.saveAs("responseBody")))
//        .exec { session => println(session("responseBody").as[String]); session }
        .pause(pause)
    }
  }

  def getSingleGame(pause: Int) = {
    feed(csvFeeder)
      .exec(
        http("Get details of single name: #{name}")
          .get(s"/videogame/#{gameId}")
          .check(jsonPath(path = "$.name").is(expected = "#{name}"))
      ).pause(pause)
  }

  def deleteGame(pause: Int, gameID: Int) = {
    exec(
      http("Delete game")
        .delete(s"/videogame/${gameID}")
        .header("authorization", "Bearer #{jwtToken}")
        .check(bodyString.is("Video game deleted"))
    ).pause(pause)
  }

  // Load test scenario
  val scn = scenario("Videogame Full Test Scenario")
    .forever {
    exec(authenticate(1, "admin", "admin"))
        .exec(getAllVideoGame(1))
        .exec(getSingleGame(1))
        .exec(createNewGame(1,1))
        .exec(deleteGame(1,1))
    }

  // set-up test parameters block
  setUp(
    scn.inject(
      nothingFor(5),
      rampUsers(USERS_COUNT).during(RAMP_UP_TIME)
    ).protocols(httpProtocol)
  ).maxDuration(TEST_DURATION)

  after {
    println("***TEST RUN IS COMPLETED!***")
  }
}
