      ******************************************************************
      * PROGRAMA CICS DE EJEMPLO PARA CONSULTAR UNA POLIZA
      * SE EXPONDRIA COMO UNA API REST A TRAVES DE Z/OS CONNECT
      ******************************************************************
       IDENTIFICATION DIVISION.
       PROGRAM-ID. GETPOLZA.
       AUTHOR.     GEMINI ARCHITECT.

       ENVIRONMENT DIVISION.

       DATA DIVISION.
       WORKING-STORAGE SECTION.
       01 WS-POLIZA-ID         PIC X(10).
       01 WS-RESPONSE-MSG      PIC X(80).

       LINKAGE SECTION.
       01 DFHCOMMAREA.
          05 LS-POLIZA-ID      PIC X(10).

       PROCEDURE DIVISION.
      ******************************************************************
      *      LOGICA PRINCIPAL
      ******************************************************************
           MOVE LS-POLIZA-ID TO WS-POLIZA-ID.

      *    ---------------------------------------------------------
      *    AQUI IRIA LA LOGICA PARA LEER LA BASE DE DATOS DB2
      *    USANDO EL WS-POLIZA-ID COMO CLAVE DE BUSQUEDA.
      *    EJEMPLO: EXEC SQL SELECT ... INTO ... FROM POLIZAS ...
      *    ---------------------------------------------------------

           STRING 'Poliza ' WS-POLIZA-ID ' encontrada.'
               DELIMITED BY SIZE
               INTO WS-RESPONSE-MSG.

      *    ---------------------------------------------------------
      *    SE DEVUELVE LA RESPUESTA AL PROGRAMA QUE LLAMO (Z/OS CONNECT)
      *    ---------------------------------------------------------
           EXEC CICS RETURN
                INPUTMSG(WS-RESPONSE-MSG)
                INPUTMSGLEN(LENGTH OF WS-RESPONSE-MSG)
           END-EXEC.

           GOBACK.
