package me.toybox.whatmovie_data_shipper.service;


import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import me.toybox.whatmovie_data_shipper.domain.Movie;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


@Component
public class KoficService {


    private ObjectMapper objectMapper;
    private RestTemplateBuilder restTemplateBuilder;

    public KoficService(RestTemplateBuilder restTemplateBuilder, ObjectMapper objectMapper) {
        this.restTemplateBuilder = restTemplateBuilder;
        this.objectMapper = objectMapper;
    }

    @Value("${kofic.key}")
    String myKey;


    Logger logger = LoggerFactory.getLogger(KoficService.class);

    public List<Movie> fetchMovieByPage(String page) throws IOException {

        String url = "http://www.kobis.or.kr/kobisopenapi/webservice/rest/movie/searchMovieList.json";
        UriComponentsBuilder uri = UriComponentsBuilder.fromUriString(url)
                .queryParam("key", myKey)
                .queryParam("openStartDt", "0000") //0000년부터
                .queryParam("openEndDt", "3000") // 전체연도를 파악하기 위해
                .queryParam("itemPerPage", "100")
                .queryParam("curPage", page);

        RestTemplate restTemplate = restTemplateBuilder.build();
        String response = restTemplate.getForObject(uri.toUriString(), String.class);

        JsonNode rootNode = objectMapper.readTree(response);
        JsonNode jsonNode = rootNode.get("movieListResult").get("movieList");

        List<Movie> movies = Collections.emptyList();
        if(jsonNode.isArray())
        {
            for (JsonNode node : jsonNode) {
                if (isPorno(node)) continue; // 성인물 filter
                String movieCd = node.get("movieCd").asText();
                movies.add(fetchMovieDetail(movieCd));
            }
        }
        return movies;
    }

    private Movie fetchMovieDetail(String movieCode) throws IOException {

        String url = "http://www.kobis.or.kr/kobisopenapi/webservice/rest/movie/searchMovieInfo.json";
        UriComponentsBuilder uri = UriComponentsBuilder.fromUriString(url)
                .queryParam("key", myKey)
                .queryParam("movieCd", movieCode);

        RestTemplate restTemplate = restTemplateBuilder.build();
        JsonNode movieDetailNode = restTemplate.getForObject(uri.toUriString(), JsonNode.class);
        JsonNode movieInfo = movieDetailNode.get("movieInfoResult").get("movieInfo");

        Movie movie = Movie.builder()
                .movieCode(movieCode)
                .build();
        movie.setMovieCode(movieCode);
        movie.setName(movieInfo.get("movieNm").asText());
        movie.setNameEn(movieInfo.get("movieNmEn").asText());

        movie.setActor(getPeopleName(movieInfo.get("actors")));
        movie.setDirector(getPeopleName(movieInfo.get("directors")));
        movie.setGenre(getGenres(movieInfo.get("genres")));
        movie.setNation(getNations(movieInfo.get("nations")));
        movie.setRating(getRatings(movieInfo.get("audits")));

        movie.setOpenDate(movieInfo.get("openDt").asText());
        movie.setShowTime(movieInfo.get("showTm").intValue());
        movie.setType(movieInfo.get("typeNm").asText());
        movie.setStatus(movieInfo.get("prdtStatNm").asText());
        movie.setProductionYear(movieInfo.get("prdtYear").asText());
        movie.setCompany(movieInfo.get("movieNmEn").asText());

        return movie;
    }

    private String getImageUrl(String movieCode) throws IOException {

        Document doc = Jsoup.connect("http://www.kobis.or.kr/kobis/business/mast/mvie/searchMovieDtl.do?code=" + movieCode).get();
        Elements imageDiv = doc.select(".rollList1 a");
        String onclick = imageDiv.get(0).attr("onclick");
        Pattern pattern = Pattern.compile("/.*jpg");
        Matcher matcher = pattern.matcher(onclick);
        String imageUri = matcher.group();

        return imageUri;
    }


    private String getDescription() {

        return null;
    }

    private String getNations(JsonNode nations) throws IOException {
        ObjectReader reader = objectMapper.readerFor(new TypeReference<List<JsonNode>>(){});
        List<JsonNode> nationJsonList = reader.readValue(nations);
        return nationJsonList.stream()
                .limit(5)
                .map(actor -> actor.get("nationNm").asText())
                .collect(Collectors.joining(","));
    }
    private String getPeopleName(JsonNode actors) throws IOException {
        ObjectReader reader = objectMapper.readerFor(new TypeReference<List<JsonNode>>(){});
        List<JsonNode> actorJsonList = reader.readValue(actors);
        return actorJsonList.stream()
                .limit(5)
                .map(actor -> actor.get("peopleNm").asText())
                .collect(Collectors.joining(","));
    }
    private String getGenres(JsonNode genre) throws IOException {
        ObjectReader reader = objectMapper.readerFor(new TypeReference<List<JsonNode>>(){});
        List<JsonNode> genreJsonList = reader.readValue(genre);
        return genreJsonList.stream()
                .map(actor -> actor.get("genreNm").asText())
                .collect(Collectors.joining(","));
    }
    private String getRatings(JsonNode rating) throws IOException {
        ObjectReader reader = objectMapper.readerFor(new TypeReference<List<JsonNode>>(){});
        List<JsonNode> ratingJsonList = reader.readValue(rating);
        return ratingJsonList.stream()
                .map(actor -> actor.get("watchGradeNm").asText())
                .collect(Collectors.joining(","));
    }

    private Boolean isPorno(JsonNode movie) {
        String genre = movie.get("genreAlt").asText();
        return genre.equals("성인물(에로)");
    }

}
