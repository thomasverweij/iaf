FROM mcr.microsoft.com/mssql/server
COPY . /
COPY dockerInit.sh /
#RUN chmod +x /dockerInit.sh
CMD /bin/bash ./entrypoint.sh