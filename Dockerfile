FROM docker.io/debian:trixie-slim AS runtime

# -----------------------------------------------------------------
# 1️⃣ Install OS packages: JRE 21, Python 3.11 (already present), LaTeX
# -----------------------------------------------------------------
ARG DEBIAN_FRONTEND=noninteractive
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        openjdk-21-jdk-headless \
        maven \
        python3 \
        python3-pip \
        pipx \
        texlive-xetex \
        texlive-fonts-recommended \
        texlive-latex-recommended \
        texlive-latex-extra \
        fontconfig \
        ca-certificates && \
    rm -rf /var/lib/apt/lists/*

ENV PATH="/root/.local/bin:${PATH}"

# Install uv
RUN pipx install --global uv==0.10.8

# Install the pdpot utility from the script
WORKDIR /install
COPY . .
RUN chmod +x ./pdpot-install.sh && ./pdpot-install.sh
RUN chmod +x ./pdpot-replicate.sh && cp pdpot-replicate.sh $HOME/.local/bin/pdpot-replicate

WORKDIR /data
