package gift.member;

import static org.assertj.core.api.Assertions.assertThat;

import gift.global.authentication.jwt.JwtToken;
import gift.global.authentication.jwt.JwtValidator;
import gift.global.authentication.jwt.TokenType;
import gift.member.persistence.repository.MemberRepository;
import gift.member.persistence.repository.WishlistRepository;
import gift.member.presentation.dto.RequestMemberDto;
import gift.member.presentation.dto.RequestWishlistDto;
import gift.member.presentation.dto.ResponseWishListDto;
import gift.product.persistence.entity.Product;
import gift.product.persistence.repository.ProductRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class MemberApiTest {
    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JwtValidator jwtValidator;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private WishlistRepository wishlistRepository;

    private static String accessToken;
    private static String refreshToken;

    @BeforeAll
    static void setUp(@Autowired TestRestTemplate restTemplate,
        @LocalServerPort int port,
        @Autowired ProductRepository productRepository
    ) {
        RequestMemberDto requestMemberDto = new RequestMemberDto(
            "test@example.com",
            "test");

        String url = "http://localhost:" + port + "/api/members/register";
        var response = restTemplate.postForEntity(url, requestMemberDto, JwtToken.class);
        var jwtToken = response.getBody();
        accessToken = jwtToken.accessToken();
        refreshToken = jwtToken.refreshToken();

        // 상품 추가
        Product product = new Product("test", "description", 1000, "http://test.com");
        productRepository.saveProduct(product);
    }

    @Test
    void testRegisterMember() {
        //given
        RequestMemberDto requestMemberDto = new RequestMemberDto(
            "test1@example.com",
            "test");

        String url = "http://localhost:" + port + "/api/members/register";

        //when
        var response = restTemplate.postForEntity(url, requestMemberDto, JwtToken.class);

        //then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        var jwtToken = response.getBody();
        var id = jwtValidator.validate("Bearer " + jwtToken.accessToken(), TokenType.ACCESS);
        var member = memberRepository.getMemberById(id);

        assertThat(member.getEmail()).isEqualTo(requestMemberDto.email());
    }

    @Test
    void testLoginMember() {
        //given
        RequestMemberDto requestMemberDto = new RequestMemberDto(
            "test@example.com",
            "test");

        String url = "http://localhost:" + port + "/api/members/login";

        //when
        var response = restTemplate.postForEntity(url, requestMemberDto, JwtToken.class);

        //then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        var jwtToken = response.getBody();
        var id = jwtValidator.validate("Bearer " + jwtToken.accessToken(), TokenType.ACCESS);
        var member = memberRepository.getMemberById(id);

        assertThat(member.getEmail()).isEqualTo(requestMemberDto.email());
    }

    @Test
    void testReissueRefreshToken() {
        //given
        String url = "http://localhost:" + port + "/api/members/reissue";
        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "Bearer " + refreshToken);

        HttpEntity<String> entity = new HttpEntity<>(headers);

        // when
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

        //then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        var reissuedTokenId = jwtValidator.validate("Bearer " + response.getBody(), TokenType.ACCESS);
        var originalTokenId = jwtValidator.validate("Bearer " + accessToken, TokenType.ACCESS);
        assertThat(reissuedTokenId).isEqualTo(originalTokenId);
    }

    @Test
    void testAddAndGetWishLists() {
        // when
        String url = "http://localhost:" + port + "/api/members/wishlists/products/1";
        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "Bearer " + accessToken);

        HttpEntity<String> entity = new HttpEntity<>(headers);

        ResponseEntity<Long> response1 = restTemplate.exchange(url, HttpMethod.POST, entity, Long.class);

        //then
        assertThat(response1.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        //given
        url = "http://localhost:" + port + "/api/members/wishlists";
        headers = new HttpHeaders();
        headers.add("Authorization", "Bearer " + accessToken);

        entity = new HttpEntity<>(headers);

        // when
        ResponseEntity<List<ResponseWishListDto>> response2 = restTemplate.exchange(
            url,
            HttpMethod.GET,
            entity,
            new ParameterizedTypeReference<List<ResponseWishListDto>>() {}
        );

        //then
        assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response2.getBody()).hasSize(1);
        var responseWishListDto = response2.getBody().getFirst();
        assertThat(responseWishListDto.productName()).isEqualTo("test");
    }

    @Test
    void testUpdateWishList() {
        // given
        String url = "http://localhost:" + port + "/api/members/wishlists/products/1";
        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "Bearer " + accessToken);

        HttpEntity<String> entity1 = new HttpEntity<>(headers);

        ResponseEntity<Long> response1 = restTemplate.exchange(url, HttpMethod.POST, entity1, Long.class);

        // when
        headers = new HttpHeaders();
        headers.add("Authorization", "Bearer " + accessToken);

        RequestWishlistDto requestWishlistDto = new RequestWishlistDto(2);

        HttpEntity<RequestWishlistDto> entity2 = new HttpEntity<>(requestWishlistDto, headers);

        ResponseEntity<Long> response2 = restTemplate.exchange(url, HttpMethod.PUT, entity2, Long.class);

        // then
        assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.OK);
        var wishlist = wishlistRepository.getWishListByMemberId(1L).getFirst();
        assertThat(wishlist.getCount()).isEqualTo(2);
    }

    @Test
    void testDeleteWishList() {
        // given
        String url = "http://localhost:" + port + "/api/members/wishlists/products/1";
        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "Bearer " + accessToken);

        HttpEntity<String> entity1 = new HttpEntity<>(headers);

        ResponseEntity<Long> response1 = restTemplate.exchange(url, HttpMethod.POST, entity1, Long.class);

        // when
        headers = new HttpHeaders();
        headers.add("Authorization", "Bearer " + accessToken);

        HttpEntity<String> entity2 = new HttpEntity<>(headers);

        ResponseEntity<Long> response2 = restTemplate.exchange(url, HttpMethod.DELETE, entity2, Long.class);

        // then
        assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.OK);
        var wishlist = wishlistRepository.getWishListByMemberId(1L);
        assertThat(wishlist).isEmpty();
    }
}