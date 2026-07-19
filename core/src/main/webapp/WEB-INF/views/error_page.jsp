<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ page contentType="text/html;charset=UTF-8" language="java" session="false" %>
<!DOCTYPE html>
<html lang="ko">
<head><meta charset="UTF-8"><title>오류</title></head>
<body><h1>요청 처리 중 오류가 발생했습니다.</h1><p><c:out value="${exception.message}"/></p></body>
</html>
