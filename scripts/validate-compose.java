import java.nio.file.*;
import java.util.*;
import org.yaml.snakeyaml.Yaml;

// YAML/schema-shape smoke only; not a replacement for `docker compose config --quiet`.
class ValidateCompose {
 @SuppressWarnings("unchecked") public static void main(String[] args)throws Exception{
  Path root=Path.of(args[0]);Yaml yaml=new Yaml();
  Map<String,Object> doc=yaml.load(Files.readString(root.resolve("compose.yaml")));
  Map<String,Object> services=(Map<String,Object>)doc.get("services");
  check(services.keySet().equals(Set.of("backend","frontend","clamav")),"Expected external-MySQL services");
  Map<String,Object> backend=(Map<String,Object>)services.get("backend");
  Map<String,Object> env=(Map<String,Object>)backend.get("environment");
  check("0.0.0.0".equals(env.get("SERVER_ADDRESS")),"Container must not bind loopback");
  check("8080".equals(env.get("SERVER_PORT")),"Backend port mismatch");
  check(!backend.containsKey("ports"),"Backend must remain private");
  check(Objects.toString(env.get("DB_URL")).contains("${DB_URL:"),"Database must come from environment");
  check(Objects.toString(env.get("STORAGE_ROOT")).equals("/app/uploads"),"Storage path mismatch");
  check(Files.isRegularFile(root.resolve("deploy/backend.Dockerfile")),"Missing backend image definition");
  check(Files.isRegularFile(root.resolve("deploy/frontend.Dockerfile")),"Missing frontend image definition");
  Map<String,Object> compatibility=yaml.load(Files.readString(root.resolve("deploy/compose.yaml")));
  check(compatibility.containsKey("include"),"Legacy entrypoint must use root config");
  long migrations;try(var files=Files.list(root.resolve("backend/src/main/resources/db/migration"))){migrations=files.filter(p->p.toString().endsWith(".sql")).count();}
  check(migrations>=6,"Missing Flyway source migrations");
  System.out.println("PASS: YAML parsing, expected services, private backend, source migrations, external database and storage wiring. Docker Engine validation is still required.");
 }
 private static void check(boolean valid,String message){if(!valid)throw new IllegalStateException(message);}
}
