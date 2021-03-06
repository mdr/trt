package com.thetestpeople.trt.webdriver.screens

import org.openqa.selenium.WebDriver
import org.openqa.selenium.By._
import com.thetestpeople.trt.webdriver.screens.RichSelenium._
import org.openqa.selenium.WebElement
import org.openqa.selenium.By

class DeletedTestsScreen(implicit automationContext: AutomationContext) extends AbstractScreen with HasMainMenu {

  def testRows: Seq[TestRow] =
    for ((rowElement, index) ← webDriver.findElements_(cssSelector("tr.test-row")).zipWithIndex)
      yield TestRow(rowElement, index)

  case class TestRow(rowElement: WebElement, index: Int) {

    def name: String = rowElement.findElement(By.cssSelector(".test-link")).getText

  }

}