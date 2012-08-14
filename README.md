![CI status](https://secure.travis-ci.org/jeluard/parent-enforcer-rule.png)

A maven enforcer rule validating all sub-modules of an aggregated project have the right parent defined.

# Usage

```xml
...
  <build>
    <plugins>
      <plugin>
        <artifactId>maven-enforcer-plugin</artifactId>
        <version>1.1</version>
	<inherited>false</inherited>
        <dependencies>
            <dependency>
                <groupId>com.github.jeluard.parent-enforcer-rule</groupId>
                <artifactId>enforcer-rule</artifactId>
                <version>1.0</version>
            </dependency>
        </dependencies>
        <executions>
          <execution>
            <id>check</id>
            <phase>verify</phase>
            <goals>
              <goal>enforce</goal>
            </goals>
            <configuration>
              <rules>
                <requireParentVersion implementation="com.github.jeluard.maven.ParentEnforcerRule" />
              </rules>
            </configuration>
          </execution>
        </executions>
      </plugin>
    </plugins>
  </build>
...
```

Released under [Apache 2 license](http://www.apache.org/licenses/LICENSE-2.0.html).
