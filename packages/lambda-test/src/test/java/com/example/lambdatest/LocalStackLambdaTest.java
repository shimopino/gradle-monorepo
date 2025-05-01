package com.example.lambdatest;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.LAMBDA;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.SQS;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.ListFunctionsResponse;

/**
 * このテストクラスでは、Testcontainersライブラリを使用してローカルに立ち上げたLocalStackコンテナ上の AWS Lambdaサービスに接続できることを検証します。
 * TestcontainersはDockerコンテナをテスト実行中に自動で起動・終了できるライブラリで、 LocalStackはAWSサービスのモック環境を提供するツールです。
 */
@Testcontainers
@DisplayName(
    "LocalStack上で起動したLambdaサービスの接続確認\n"
        + "・LocalStackContainerでLambda/SQSサービスを起動\n"
        + "・AWS SDK v2のLambdaClientをLocalStackエンドポイントで構築\n"
        + "・listFunctions()で空リスト応答を検証、関数未登録状態を保証")
public class LocalStackLambdaTest {

  @Container
  // @Container: これによりテスト実行前にコンテナをセットアップし、テスト後に自動破棄される
  // LocalStackContainer: LocalStackをDockerコンテナで起動し、指定したAWSサービス(SQS, Lambda)をエミュレート
  static LocalStackContainer localstack =
      new LocalStackContainer(DockerImageName.parse("localstack/localstack:latest"))
          .withServices(LAMBDA, SQS);

  @Test
  @DisplayName("Lambdaエンドポイントへの接続とlistFunctions()応答の確認テスト")
  void lambdaServiceIsAccessible() {
    // LambdaClientを構築: LocalStackコンテナのエンドポイント、リージョン、認証情報を利用して接続
    LambdaClient lambda =
        LambdaClient.builder()
            .endpointOverride(localstack.getEndpointOverride(LAMBDA)) // LocalStackのLambdaエンドポイントを指定
            .region(Region.of(localstack.getRegion())) // コンテナが使用するリージョンを指定
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(
                        localstack.getAccessKey(), // コンテナから取得したダミーのアクセスキー
                        localstack.getSecretKey() // コンテナから取得したダミーのシークレットキー
                        )))
            .build();

    // listFunctions(): Lambda関数の一覧を取得するAPIリクエストを実行
    ListFunctionsResponse response = lambda.listFunctions();
    // assertTrue: ローカル起動直後は関数が未登録のため、返却されるリストが空であることを検証
    assertTrue(response.functions().isEmpty(), "Lambda 関数が空であることを確認");

    // リソース解放: LambdaClientのクローズ
    lambda.close();
  }
}
