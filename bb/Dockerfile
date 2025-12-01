FROM clojure:tools-deps AS builder

# RUN bash < <(curl -s https://raw.githubusercontent.com/babashka/babashka/master/install)

# Install curl and bash, then run the babashka install script, and clean up apt lists
RUN apt-get update && apt-get install -y curl \
    && curl -sLO https://raw.githubusercontent.com/babashka/babashka/master/install \
    && chmod +x install \
    && ./install \
    && rm install \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY bb.edn .

COPY src src

RUN bb uberjar server.jar -m server

FROM babashka/babashka

WORKDIR /app

COPY --from=builder /app/server.jar .

EXPOSE 9000 3300

ENTRYPOINT [ "bb", "server.jar" ]
